-- get random metro areas from a DB
-- run it like psql [connection params] -o outfile -f getRandomMetros.sql

\pset tuples_only on
\pset format unaligned

SELECT id
-- window functions not allowed in WHERE clause
FROM (SELECT m.id, row_number() OVER (ORDER BY ST_Area(m.the_geom) * 1000 + count(g.feeds_id) DESC) AS rownum 
      FROM metroarea m JOIN metroarea_ntdagency mn ON (m.id = mn.metroarea_id)
                       JOIN ntdagency a ON (a.id = mn.agencies_id)
                       JOIN ntdagency_gtfsfeed g ON (g.ntdagency_id = a.id)
      WHERE (m.disabled = FALSE OR m.disabled IS NULL)
      GROUP BY m.id
      HAVING count(g.feeds_id) > 0) m
                       
WHERE rownum % 3 = 0
-- manually exclude nyc for now
AND id <> 1539;