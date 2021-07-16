# Dynamic slicing

**TL;DR;** As soon as we drop the Join with the tags times seem to improve.

Best Option: using `x2.text_mask LIKE '10%'` (where `text_mask character(10)` contains a string rerepsentation of a 10-digit binary hash of the persistenceId)

Using `x2.text_mask LIKE '10%'` without any index on `text_mask` already produces a stable query execution around the 10ms range which is twice faster than the best we ever got using a join with the tag table.



## Hashing the persistenceID


```sql
ALTER TABLE public.event_journal
    ADD COLUMN bit_mask bit(10);
ALTER TABLE public.event_journal
    ADD COLUMN type_hint character varying(255);
ALTER TABLE public.event_journal
    ADD COLUMN text_mask character(10);
```

```sql
-- create a hashing function
create function h_bigint(text) returns bigint as $$
 select ('x'||substr(md5($1),1,16))::bit(64)::bigint;
$$ language sql;
-- test the hashing function
select persistence_id , ordering, 
  h_bigint(persistence_id) as hashcode,  
  mod(h_bigint(persistence_id), 1024)::bit(10)
from event_journal
order by  persistence_id


-- alter the tabla to add the hashcode column and the type_hint column
update  event_journal set bit_mask=mod(h_bigint(persistence_id), 1024)::bit(10);
update  event_journal set type_hint=substring( persistence_id  FROM 0 FOR position('|' IN persistence_id) );
update  event_journal set text_mask=bit_mask::text;

ALTER TABLE public.event_journal
    ALTER COLUMN bit_mask SET NOT NULL;
ALTER TABLE public.event_journal
    ALTER COLUMN text_mask SET NOT NULL;
ALTER TABLE public.event_journal
    ALTER COLUMN type_hint SET NOT NULL;
```



### Using the bitmask (not indexed)

```sql
shopping-cart=# VACUUM ANALYSE;
VACUUM
shopping-cart=# explain analyse select x2."ordering",
    x2."persistence_id",
    x2."sequence_number",
    x2."writer",
    x2."write_timestamp",
    x2."event_payload",
    x2."event_ser_id",
    x2."event_ser_manifest"
from "event_journal" x2
where (
    -- bit(2) means I have 4 slices (which is comparable to 5 tags)
        x2.bit_mask::bit(2)=B'10'
        and x2.type_hint='ShoppingCart'
           and (
                   (x2."ordering" > 149627)
                   and (x2."ordering" <= 151627)
                )
        )
order by x2."ordering" limit 500;
                                                                        QUERY PLAN
----------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=0.42..3893.09 rows=9 width=178) (actual time=0.028..8.320 rows=500 loops=1)
   ->  Index Scan using event_journal_ordering_idx on event_journal x2  (cost=0.42..3893.09 rows=9 width=178) (actual time=0.018..3.673 rows=500 loops=1)
         Index Cond: ((ordering > 149627) AND (ordering <= 151627))
         Filter: (((type_hint)::text = 'ShoppingCart'::text) AND ((bit_mask)::bit(2) = '10'::"bit"))
         Rows Removed by Filter: 1265
 Planning Time: 0.207 ms
 Execution Time: 10.473 ms
(7 rows)
```

```sql

explain analyse select x2."ordering",
    x2."persistence_id",
    x2."sequence_number",
    x2."writer",
    x2."write_timestamp",
    x2."event_payload",
    x2."event_ser_id",
    x2."event_ser_manifest"
from "event_journal" x2
where (
    -- bit(2) means I have 4 slices (which is comparable to 5 tags)
        bit_mask::bit(2)=B'10'
        and x2.type_hint='ShoppingCart'
           and (
                   (x2."ordering" > 141815)
                   and (x2."ordering" <= 151627)
                )
        )
order by x2."ordering" limit 500;
                                                                      QUERY PLAN
-------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=10325.81..10325.93 rows=47 width=178) (actual time=30.973..37.451 rows=500 loops=1)
   ->  Sort  (cost=10325.81..10325.93 rows=47 width=178) (actual time=30.963..33.149 rows=500 loops=1)
         Sort Key: ordering
         Sort Method: top-N heapsort  Memory: 161kB
         ->  Bitmap Heap Scan on event_journal x2  (cost=307.39..10324.51 rows=47 width=178) (actual time=0.422..17.669 rows=2655 loops=1)
               Recheck Cond: ((ordering > 141815) AND (ordering <= 151627))
               Filter: (((type_hint)::text = 'ShoppingCart'::text) AND ((bit_mask)::bit(2) = '10'::"bit"))
               Rows Removed by Filter: 7157
               Heap Blocks: exact=322
               ->  Bitmap Index Scan on event_journal_ordering_idx  (cost=0.00..307.38 rows=9496 width=0) (actual time=0.378..0.382 rows=9812 loops=1)
                     Index Cond: ((ordering > 141815) AND (ordering <= 151627))
 Planning Time: 0.107 ms
 Execution Time: 39.635 ms
(13 rows)
```

```sql

explain analyse select x2."ordering",
    x2."persistence_id",
    x2."sequence_number",
    x2."writer",
    x2."write_timestamp",
    x2."event_payload",
    x2."event_ser_id",
    x2."event_ser_manifest"
from "event_journal" x2
where (
    -- bit(2) means I have 4 slices (which is comparable to 5 tags)
        bit_mask::bit(2)=B'10'
        and x2.type_hint='ShoppingCart'
           and (
                   (x2."ordering" > 15)
                   and (x2."ordering" <= 151627)
                )
        )
order by x2."ordering" limit 500;
                                                                          QUERY PLAN
--------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=11916.65..11974.98 rows=500 width=178) (actual time=148.535..159.699 rows=500 loops=1)
   ->  Gather Merge  (cost=11916.65..11990.39 rows=632 width=178) (actual time=148.522..155.608 rows=500 loops=1)
         Workers Planned: 2
         Workers Launched: 2
         ->  Sort  (cost=10916.62..10917.41 rows=316 width=178) (actual time=144.308..145.741 rows=354 loops=3)
               Sort Key: ordering
               Sort Method: top-N heapsort  Memory: 159kB
               Worker 0:  Sort Method: top-N heapsort  Memory: 249kB
               Worker 1:  Sort Method: top-N heapsort  Memory: 240kB
               ->  Parallel Seq Scan on event_journal x2  (cost=0.00..10903.50 rows=316 width=178) (actual time=0.021..77.923 rows=13604 loops=3)
                     Filter: ((ordering > 15) AND (ordering <= 151627) AND ((type_hint)::text = 'ShoppingCart'::text) AND ((bit_mask)::bit(2) = '10'::"bit"))
                     Rows Removed by Filter: 36938
 Planning Time: 0.150 ms
 Execution Time: 161.684 ms
(14 rows)
```



### Using the textmask-LIKE (textmask is not indexed)

```sql
shopping-cart=# VACUUM ANALYSE;
VACUUM
shopping-cart=# explain analyse select x2."ordering",
    x2."persistence_id",
    x2."sequence_number",
    x2."writer",
    x2."write_timestamp",
    x2."event_payload",
    x2."event_ser_id",
    x2."event_ser_manifest"
from "event_journal" x2
where (
        x2.text_mask LIKE '10%'
        and x2.type_hint='ShoppingCart'
           and (
                   (x2."ordering" > 149627)
                   and (x2."ordering" <= 151627)
                )
        )
order by x2."ordering" limit 500;
                                                                         QUERY PLAN
------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=0.42..3947.54 rows=500 width=178) (actual time=0.029..7.776 rows=500 loops=1)
   ->  Index Scan using event_journal_ordering_idx on event_journal x2  (cost=0.42..4073.84 rows=516 width=178) (actual time=0.022..3.349 rows=500 loops=1)
         Index Cond: ((ordering > 149627) AND (ordering <= 151627))
         Filter: ((text_mask ~~ '10%'::text) AND ((type_hint)::text = 'ShoppingCart'::text))
         Rows Removed by Filter: 1265
 Planning Time: 0.207 ms
 Execution Time: 10.059 ms
(7 rows)
```

```sql

shopping-cart=# explain analyse select x2."ordering",
    x2."persistence_id",
    x2."sequence_number",
    x2."writer",
    x2."write_timestamp",
    x2."event_payload",
    x2."event_ser_id",
    x2."event_ser_manifest"
from "event_journal" x2
where (
        x2.text_mask LIKE '10%'
        and x2.type_hint='ShoppingCart'
           and (
                   (x2."ordering" > 141815)
                   and (x2."ordering" <= 151627)
                )
        )
order by x2."ordering" limit 500;

                                                                          QUERY PLAN
--------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=0.42..2901.53 rows=500 width=178) (actual time=0.033..7.800 rows=500 loops=1)
   ->  Index Scan using event_journal_ordering_idx on event_journal x2  (cost=0.42..15242.87 rows=2627 width=178) (actual time=0.022..3.493 rows=500 loops=1)
         Index Cond: ((ordering > 141815) AND (ordering <= 151627))
         Filter: ((text_mask ~~ '10%'::text) AND ((type_hint)::text = 'ShoppingCart'::text))
         Rows Removed by Filter: 1408
 Planning Time: 0.121 ms
 Execution Time: 10.025 ms
(7 rows)
```

```sql

shopping-cart=# explain analyse select x2."ordering",
    x2."persistence_id",
    x2."sequence_number",
    x2."writer",
    x2."write_timestamp",
    x2."event_payload",
    x2."event_ser_id",
    x2."event_ser_manifest"
from "event_journal" x2
where (
        x2.text_mask LIKE '10%'
        and x2.type_hint='ShoppingCart'
           and (
                   (x2."ordering" > 15)
                   and (x2."ordering" <= 151627)
                )
        )
order by x2."ordering" limit 500;
                                                                          QUERY PLAN
---------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=0.42..402.71 rows=500 width=178) (actual time=0.039..8.537 rows=500 loops=1)
   ->  Index Scan using event_journal_ordering_idx on event_journal x2  (cost=0.42..32511.28 rows=40407 width=178) (actual time=0.025..3.754 rows=500 loops=1)
         Index Cond: ((ordering > 15) AND (ordering <= 151627))
         Filter: ((text_mask ~~ '10%'::text) AND ((type_hint)::text = 'ShoppingCart'::text))
         Rows Removed by Filter: 1322
 Planning Time: 0.167 ms
 Execution Time: 11.114 ms
(7 rows)
```

### APPENDIX: Using the textmask - IN (textmask is not indexed)

```sql
explain analyse select x2."ordering",
    x2."persistence_id",
    x2."sequence_number",
    x2."writer",
    x2."write_timestamp",
    x2."event_payload",
    x2."event_ser_id",
    x2."event_ser_manifest"
from "event_journal" x2
where (
        x2.text_mask IN (
            '1010101011', '1010101010', '1010101001', '1010101000'
            )
        and x2.type_hint='ShoppingCart'
           and (
                   (x2."ordering" > 149627)
                   and (x2."ordering" <= 151627)
                )
        )
order by x2."ordering" limit 500;

                                                                       QUERY PLAN
---------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=0.42..4078.68 rows=10 width=178) (actual time=0.362..0.912 rows=5 loops=1)
   ->  Index Scan using event_journal_ordering_idx on event_journal x2  (cost=0.42..4078.68 rows=10 width=178) (actual time=0.351..0.849 rows=5 loops=1)
         Index Cond: ((ordering > 149627) AND (ordering <= 151627))
         Filter: (((type_hint)::text = 'ShoppingCart'::text) AND (text_mask = ANY ('{1010101011,1010101010,1010101001,1010101000}'::bpchar[])))
         Rows Removed by Filter: 1995
 Planning Time: 0.159 ms
 Execution Time: 0.966 ms
(7 rows)
```
This is very fast because the returned resultset is very small (only picked 4/1024 slices).