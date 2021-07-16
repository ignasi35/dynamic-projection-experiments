# Ideas

## JDBC

### Filter over a single index [GOOD]

This is now https://github.com/akka/akka-persistence-jdbc/pull/560

Taking the baseline queryByTag queries, prefer using the tab columns when filtering so the journal index is only used for ordering

1. :

```sql
shopping-cart=# explain analyse select x2."ordering",
    x2."persistence_id",
    x2."sequence_number",
    x2."writer",
    x2."write_timestamp",
    x2."event_payload",
    x2."event_ser_id",
    x2."event_ser_manifest"
from "event_journal" x2, "event_tag" x3
where (
        (x3."tag" = 'carts-0')
           and (
                   (x3."event_id" > 149627)
                   and (x3."event_id" <= 151627))
          )
    and (x2."ordering" = x3."event_id")
order by x2."ordering" limit 500;
                                                                         QUERY PLAN
-------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=0.84..379.30 rows=45 width=178) (actual time=0.065..15.550 rows=402 loops=1)
   ->  Nested Loop  (cost=0.84..379.30 rows=45 width=178) (actual time=0.050..11.572 rows=402 loops=1)
         ->  Index Only Scan using event_tag_pkey on event_tag x3  (cost=0.42..7.61 rows=45 width=8) (actual time=0.018..2.106 rows=402 loops=1)
               Index Cond: ((event_id > 151815) AND (event_id <= 153815) AND (tag = 'carts-0'::text))
               Heap Fetches: 0
         ->  Index Scan using event_journal_ordering_idx on event_journal x2  (cost=0.42..8.26 rows=1 width=178) (actual time=0.007..0.007 rows=1 loops=402)
               Index Cond: (ordering = x3.event_id)
 Planning Time: 0.261 ms
 Execution Time: 17.569 ms
(9 rows)
```

2. Then we try a big offset and equal limit. 

```sql
shopping-cart=# explain analyse select x2."ordering",
    x2."persistence_id",
    x2."sequence_number",
    x2."writer",
    x2."write_timestamp",
    x2."event_payload",
    x2."event_ser_id",
    x2."event_ser_manifest"
from "event_journal" x2, "event_tag" x3
where (
        (x3."tag" = 'carts-0')
           and (
                   (x3."event_id" > 141815)
                   and (x3."event_id" <= 153815))
          )
    and (x2."ordering" = x3."event_id")
order by x2."ordering" limit 500;
                                                                         QUERY PLAN
-------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=0.84..2038.09 rows=271 width=178) (actual time=0.059..18.178 rows=500 loops=1)
   ->  Nested Loop  (cost=0.84..2038.09 rows=271 width=178) (actual time=0.049..13.713 rows=500 loops=1)
         ->  Index Only Scan using event_tag_pkey on event_tag x3  (cost=0.42..43.53 rows=271 width=8) (actual time=0.021..2.439 rows=500 loops=1)
               Index Cond: ((event_id > 141815) AND (event_id <= 153815) AND (tag = 'carts-0'::text))
               Heap Fetches: 0
         ->  Index Scan using event_journal_ordering_idx on event_journal x2  (cost=0.42..7.36 rows=1 width=178) (actual time=0.007..0.007 rows=1 loops=500)
               Index Cond: (ordering = x3.event_id)
 Planning Time: 0.192 ms
 Execution Time: 20.472 ms
(9 rows)

```

3. This is now a query for a completely new projection that has the full journal to catch up on:

```sql
explain analyse select x2."ordering",
    x2."persistence_id",
    x2."sequence_number",
    x2."writer",
    x2."write_timestamp",
    x2."event_payload",
    x2."event_ser_id",
    x2."event_ser_manifest"
from "event_journal" x2, "event_tag" x3
where (
        (x3."tag" = 'carts-0')
           and (
                   (x3."event_id" > 15)
                   and (x3."event_id" <= 153815))
          )
    and (x2."ordering" = x3."event_id")
order by x2."ordering" limit 500;
                                                                             QUERY PLAN
---------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=1.13..219.43 rows=500 width=178) (actual time=0.238..16.581 rows=500 loops=1)
   ->  Merge Join  (cost=1.13..13757.42 rows=31508 width=178) (actual time=0.228..12.262 rows=500 loops=1)
         Merge Cond: (x2.ordering = x3.event_id)
         ->  Index Scan using event_journal_ordering_idx on event_journal x2  (cost=0.42..8426.16 rows=153544 width=178) (actual time=0.012..2.934 rows=515 loops=1)
         ->  Index Only Scan using event_tag_pkey on event_tag x3  (cost=0.42..4595.69 rows=31508 width=8) (actual time=0.023..2.304 rows=500 loops=1)
               Index Cond: ((event_id > 15) AND (event_id <= 153815) AND (tag = 'carts-0'::text))
               Heap Fetches: 0
 Planning Time: 0.221 ms
 Execution Time: 18.745 ms
(9 rows)
```

*CONCLUSION*: Using the tag table column (`event_id`) for filtering makes the execution time of the query much faster and stable.



### Filter and order over a single index [UNNECESSARY after the one above]

See https://github.com/akka/akka-persistence-jdbc/pull/560

Taking the baseline queryByTag queries, prefer using the tab columns when filtering so the journal index is only used for ordering

1. A somewhat regular query:

```sql
shopping-cart=# explain analyse select x2."ordering",
    x2."persistence_id",
    x2."sequence_number",
    x2."writer",
    x2."write_timestamp",
    x2."event_payload",
    x2."event_ser_id",
    x2."event_ser_manifest"
from "event_journal" x2, "event_tag" x3
where (
        (x3."tag" = 'carts-0')
           and (
                   (x3."event_id" > 151815)
                   and (x3."event_id" <= 153815))
          )
    and (x2."ordering" = x3."event_id")
order by x3."event_id" limit 500;

                                                                         QUERY PLAN
-------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=0.84..379.30 rows=45 width=186) (actual time=0.063..14.669 rows=402 loops=1)
   ->  Nested Loop  (cost=0.84..379.30 rows=45 width=186) (actual time=0.051..10.953 rows=402 loops=1)
         ->  Index Only Scan using event_tag_pkey on event_tag x3  (cost=0.42..7.61 rows=45 width=8) (actual time=0.019..2.092 rows=402 loops=1)
               Index Cond: ((event_id > 151815) AND (event_id <= 153815) AND (tag = 'carts-0'::text))
               Heap Fetches: 0
         ->  Index Scan using event_journal_ordering_idx on event_journal x2  (cost=0.42..8.26 rows=1 width=178) (actual time=0.007..0.007 rows=1 loops=402)
               Index Cond: (ordering = x3.event_id)
 Planning Time: 0.215 ms
 Execution Time: 16.660 ms
(9 rows)
```

2. Then we try a big offset and equal limit. 

```sql
shopping-cart=# explain analyse select x2."ordering",
    x2."persistence_id",
    x2."sequence_number",
    x2."writer",
    x2."write_timestamp",
    x2."event_payload",
    x2."event_ser_id",
    x2."event_ser_manifest"
from "event_journal" x2, "event_tag" x3
where (
        (x3."tag" = 'carts-0')
           and (
                   (x3."event_id" > 141815)
                   and (x3."event_id" <= 153815))
          )
    and (x2."ordering" = x3."event_id")
order by x3."event_id" limit 500;

                                                                         QUERY PLAN
-------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=0.84..2038.09 rows=271 width=186) (actual time=0.067..18.823 rows=500 loops=1)
   ->  Nested Loop  (cost=0.84..2038.09 rows=271 width=186) (actual time=0.055..14.065 rows=500 loops=1)
         ->  Index Only Scan using event_tag_pkey on event_tag x3  (cost=0.42..43.53 rows=271 width=8) (actual time=0.024..2.660 rows=500 loops=1)
               Index Cond: ((event_id > 141815) AND (event_id <= 153815) AND (tag = 'carts-0'::text))
               Heap Fetches: 0
         ->  Index Scan using event_journal_ordering_idx on event_journal x2  (cost=0.42..7.36 rows=1 width=178) (actual time=0.008..0.008 rows=1 loops=500)
               Index Cond: (ordering = x3.event_id)
 Planning Time: 0.215 ms
 Execution Time: 21.266 ms
(9 rows)
```

3. This is now a query for a completely new projection that has the full journal to catch up on:

```sql
explain analyse select x2."ordering",
    x2."persistence_id",
    x2."sequence_number",
    x2."writer",
    x2."write_timestamp",
    x2."event_payload",
    x2."event_ser_id",
    x2."event_ser_manifest"
from "event_journal" x2, "event_tag" x3
where (
        (x3."tag" = 'carts-0')
           and (
                   (x3."event_id" > 15)
                   and (x3."event_id" <= 153815))
          )
    and (x2."ordering" = x3."event_id")
order by x3."event_id" limit 500;


                                                                             QUERY PLAN
---------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=1.13..219.43 rows=500 width=186) (actual time=0.229..17.719 rows=500 loops=1)
   ->  Merge Join  (cost=1.13..13757.42 rows=31508 width=186) (actual time=0.217..13.210 rows=500 loops=1)
         Merge Cond: (x2.ordering = x3.event_id)
         ->  Index Scan using event_journal_ordering_idx on event_journal x2  (cost=0.42..8426.16 rows=153544 width=178) (actual time=0.014..2.956 rows=515 loops=1)
         ->  Index Only Scan using event_tag_pkey on event_tag x3  (cost=0.42..4595.69 rows=31508 width=8) (actual time=0.025..2.509 rows=500 loops=1)
               Index Cond: ((event_id > 15) AND (event_id <= 153815) AND (tag = 'carts-0'::text))
               Heap Fetches: 0
 Planning Time: 0.220 ms
 Execution Time: 20.184 ms
(9 rows)

```

*CONCLUSION*: There doesn't see to be a significant difference in performance between this second experiment and the one involving only filtering.


### Drop PK on tags, use custom index

```sql
CREATE INDEX tags_tag_ordering_index
    ON public.event_tag USING btree
    (tag ASC NULLS LAST, event_id ASC NULLS LAST)
;
```

