SELECT
  COUNT(DISTINCT process.upid) AS process_count,
  CAST(MAX(process.mem_resident_kb) AS INTEGER) AS max_rss_kb
FROM process
WHERE process.name IS NOT NULL;
