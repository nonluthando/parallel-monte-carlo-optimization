# CV Description

## Short version (for a projects list / one-liner)

> Parallel Monte Carlo Optimisation — Java, Fork/Join Framework — Converted a serial optimisation algorithm to a parallel Fork/Join implementation with a lightweight web UI; benchmarked serial vs. parallel performance across grid sizes and core counts.

## CV bullet points (pick 3–4)

- Designed and implemented a Fork/Join-based parallel Monte Carlo optimisation algorithm in Java, applying divide-and-conquer recursion with a tuned sequential cutoff to balance task overhead against throughput.
- Built a dependency-free Java web server (`com.sun.net.httpserver`) exposing a JSON API to run and compare serial/parallel searches on demand, paired with a browser-based UI for configuring runs and visualising results as a heatmap.
- Validated correctness by comparing serial and parallel outputs across repeated runs, and benchmarked scalability by varying grid size, search density, and CPU core count using median execution time to control for JVM/OS noise.
- Analysed real-world limits of parallel speedup, identifying where multi-core execution meaningfully improves runtime versus where overhead dominates.

## Skills/tech to tag

Java, Concurrency, Fork/Join Framework, Multithreading, Performance Benchmarking, HTTP APIs, HTML/CSS/JS
