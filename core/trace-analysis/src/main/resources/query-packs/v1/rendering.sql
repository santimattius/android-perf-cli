SELECT
  CAST(
    SUM(
      IIF(
        thread_state.state = 'R' OR thread_state.state = 'R+',
        IIF(
          thread_state.dur = -1,
          trace_end() - thread_state.ts,
          thread_state.dur
        ),
        0
      )
    ) / 1e6 AS INTEGER
  ) AS main_thread_blocked_ms,
  (
    SELECT COUNT(*)
    FROM slice
    WHERE slice.name GLOB '*GFX*' OR slice.name GLOB '*Choreographer*' OR slice.name GLOB '*Draw*'
  ) AS gfx_slice_count,
  CAST(
    (
      SELECT SUM(IIF(slice.dur = -1, trace_end() - slice.ts, slice.dur))
      FROM slice
      WHERE slice.name GLOB '*GFX*' OR slice.name GLOB '*Choreographer*' OR slice.name GLOB '*Draw*'
    ) / 1e6 AS INTEGER
  ) AS gfx_total_duration_ms;
