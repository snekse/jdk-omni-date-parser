# Step 6 — Run Benchmarks

Once tests pass and benchmark inputs are updated (or the user opted out), invoke:

```
/update-bench-results
```

This runs the JMH benchmarks, checks for regressions, and updates the README performance
table if numbers have shifted more than ~5%.

Do not skip this step even if benchmark inputs were not changed — the new parsing logic
may affect throughput for existing inputs.
