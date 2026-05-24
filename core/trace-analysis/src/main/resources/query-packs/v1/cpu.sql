SELECT
  slice.name AS top_slice_name,
  CAST(IIF(slice.dur = -1, trace_end() - slice.ts, slice.dur) / 1e6 AS INTEGER) AS top_slice_duration_ms
FROM slice
ORDER BY IIF(slice.dur = -1, trace_end() - slice.ts, slice.dur) DESC
LIMIT 1;
