-- Calculate the number of stops in each metro, and sort descending
SELECT m.id, m.name, sum(g.stops)
  FROM MetroArea m
    JOIN MetroArea_NtdAgency mn ON (m.id = mn.metroarea_id)
    JOIN NtdAgency n ON (mn.agencies_id = n.id)
    JOIN NtdAgency_GtfsFeed ng ON (ng.ntdagency_id = n.id)
    JOIN GtfsFeed g ON (ng.feeds_id = g.id)
  WHERE g.supersededBy_id IS NULL
    AND g.status = 'SUCCESSFUL'
    AND NOT g.disabled
  GROUP BY m.id
  ORDER BY 3 DESC;
  