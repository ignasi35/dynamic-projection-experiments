# NOTES

## General requirements

From JRoper's [comment](https://github.com/lightbend/akka-platform-meta/discussions/227#discussioncomment-908163)

1.    Ensure polling queries are efficient (ie, don't read too much data).
1.    Ensure write hotspots are avoided.
1.    Allow multiple nodes to run read side processors.



## General tips and tricks

`PGPASSWORD=shopping-cart psql -U shopping-cart -h localhost shopping-cart`

```sql
select count(*) from event_journal;
 count
--------
 153815
(1 row)
```

## links to how each DB indexes

1. PG
- https://www.postgresql.org/docs/13/indexes-multicolumn.html
- https://distributedsystemsauthority.com/index-efficiency-and-maintenance-postgresql-12-high-performance-guide-part-5-12/

2. MySQL
- https://www.lullabot.com/articles/slow-queries-check-the-cardinality-of-your-mysql-indexes
- https://www.mysqltutorial.org/mysql-index/mysql-index-cardinality/
- https://www.amazon.com/High-Performance-MySQL-Optimization-Replication/dp/1449314287 

3. Cassandra
- https://docs.datastax.com/en/cql-oss/3.3/cql/cql_using/useWhenIndex.html

