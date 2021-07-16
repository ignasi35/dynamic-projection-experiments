# JDBC Starting point


## Starting point 

Using Slick's default `queryByTag` SQL statement I made a couple of tests (with small resultset and with a big resultset so limit kicks in).

**NOTE**: all query plans shown here are the result of multiple executions of the same analisys. I've been running all three queries multiple times randomly to make sure I was not getting exceptional query plans.

1. First, I use a reasonable offset and limit:

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
                   (x2."ordering" > 151815)
                   and (x2."ordering" <= 153815))
          )
    and (x2."ordering" = x3."event_id")
order by x2."ordering" limit 500;
                                                                            QUERY PLAN
------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=0.84..875.91 rows=45 width=178) (actual time=0.155..52.980 rows=402 loops=1)
   ->  Nested Loop  (cost=0.84..875.91 rows=45 width=178) (actual time=0.140..49.324 rows=402 loops=1)
         ->  Index Scan using event_journal_ordering_idx on event_journal x2  (cost=0.42..19.11 rows=220 width=178) (actual time=0.081..11.503 rows=2000 loops=1)
               Index Cond: ((ordering > 151815) AND (ordering <= 153815))
         ->  Index Only Scan using event_tag_pkey on event_tag x3  (cost=0.42..3.89 rows=1 width=8) (actual time=0.008..0.008 rows=0 loops=2000)
               Index Cond: ((event_id = x2.ordering) AND (tag = 'carts-0'::text))
               Heap Fetches: 402
 Planning Time: 0.285 ms
 Execution Time: 54.818 ms
(9 rows)
```
It produces a consistent 55ms execution time.

2. Then we try a big offset and equal limit. This processor is lagging behind...

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
                   (x2."ordering" > 141815)
                   and (x2."ordering" <= 153815))
          )
    and (x2."ordering" = x3."event_id")
order by x2."ordering" limit 500;
                                                                                   QUERY PLAN
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=2936.03..2936.71 rows=270 width=178) (actual time=448.314..454.791 rows=500 loops=1)
   ->  Sort  (cost=2936.03..2936.71 rows=270 width=178) (actual time=448.303..450.377 rows=500 loops=1)
         Sort Key: x2.ordering
         Sort Method: top-N heapsort  Memory: 195kB
         ->  Hash Join  (cost=101.96..2925.13 rows=270 width=178) (actual time=369.357..436.492 rows=2399 loops=1)
               Hash Cond: (x3.event_id = x2.ordering)
               ->  Seq Scan on event_tag x3  (cost=0.00..2740.32 rows=31560 width=8) (actual time=0.012..156.151 rows=31132 loops=1)
                     Filter: ((tag)::text = 'carts-0'::text)
                     Rows Removed by Filter: 122683
               ->  Hash  (cost=85.44..85.44 rows=1322 width=178) (actual time=133.680..133.693 rows=12000 loops=1)
                     Buckets: 16384 (originally 2048)  Batches: 1 (originally 1)  Memory Usage: 2671kB
                     ->  Index Scan using event_journal_ordering_idx on event_journal x2  (cost=0.42..85.44 rows=1322 width=178) (actual time=0.038..70.999 rows=12000 loops=1)
                           Index Cond: ((ordering > 141815) AND (ordering <= 153815))
 Planning Time: 0.225 ms
 Execution Time: 456.892 ms
(15 rows)
```
And the execution time does increase and even the query plan looks completely different (including a sequential scan to filter by tag).

3. Here's were things get weird. This is now a query for a completely new projection that has the full journal to catch up on

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
                   (x2."ordering" > 15)
                   and (x2."ordering" <= 153815))
          )
    and (x2."ordering" = x3."event_id")
order by x2."ordering" limit 500;
                                                                             QUERY PLAN
---------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=1.15..219.65 rows=500 width=178) (actual time=0.160..17.538 rows=500 loops=1)
   ->  Merge Join  (cost=1.15..13770.12 rows=31508 width=178) (actual time=0.150..12.829 rows=500 loops=1)
         Merge Cond: (x2.ordering = x3.event_id)
         ->  Index Scan using event_journal_ordering_idx on event_journal x2  (cost=0.42..9193.54 rows=153527 width=178) (actual time=0.019..2.816 rows=500 loops=1)
               Index Cond: ((ordering > 15) AND (ordering <= 153815))
         ->  Index Only Scan using event_tag_pkey on event_tag x3  (cost=0.42..3834.17 rows=31511 width=8) (actual time=0.018..2.567 rows=511 loops=1)
               Index Cond: (tag = 'carts-0'::text)
               Heap Fetches: 0
 Planning Time: 0.209 ms
 Execution Time: 19.825 ms
(10 rows)
```
And both the query plan and execution time go back to similar values in case `1.`. 