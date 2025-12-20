# Parallel Monte Carlo Optimisation (Java)

## Overview
Parallelised a Monte Carlo optimisation algorithm in **Java** using the **Fork/Join framework** to evaluate how multi-core execution impacts performance. The project focuses on **parallel algorithm design, correctness validation, and performance benchmarking**.



## Why This Project
- Demonstrates **concurrency and parallelism** with Java Fork/Join  
- Shows **systems-level performance reasoning**  
- Uses **controlled benchmarks** to analyse scalability and overhead  
- Explains **when parallelism helps and when it doesn’t**

## What I Built

### Parallel Implementation
- Converted a serial Monte Carlo optimisation algorithm into a **Fork/Join-based parallel solution**
- Applied **divide-and-conquer recursion** with a **sequential cutoff** to balance overhead and throughput
- Executed independent searches concurrently on shared memory

### Orchestration & Timing
- Managed parallel execution using a `ForkJoinPool`
- Measured execution time consistently for fair serial vs parallel comparisons


## Validation & Testing
- Verified correctness by comparing **serial and parallel outputs** under identical inputs
- Repeated runs to confirm result consistency
- Validated optimisation behaviour using known test functions

## Performance Benchmarking
### Benchmark Design
- Compared **
  serial vs parallel runtime
- **Varied grid size**, **search density**, and **CPU core count**
- Used **median execution time** to reduce noise from JVM scheduling

### Key Findings
- Parallel execution provides clear speedup for large workloads
- Ideal speedup is constrained by sequential components and scheduling overhead
- Highlights real-world limits of parallel scalability



## Tech Stack
- **Java**
- **Fork/Join Framework**
- Shared-memory parallelism
- CLI-based execution
- Performance benchmarking


## Project Structure
- `TerrainArea.java` – Terrain representation with lazy evaluation  
- `MonteCarloMinimization.java` – Serial baseline implementation  
- `SearchParallel.java` – Fork/Join parallel task  
- `MonteCarloMinimizationParallel.java` – Parallel execution driver  



## How to Run

### Compile
```bash
make

Run (Parallel)

java MonteCarloMinimizationParallel <rows> <cols> <xmin> <xmax> <ymin> <ymax> <search_density>

Example

java MonteCarloMinimizationParallel 5000 5000 -2 2 -2 2 0.1

What This Shows
	•	Ability to design and reason about parallel systems
	•	Comfort with debugging, benchmarking, and trade-off analysis
	•	Focus on evidence-based performance decisions

