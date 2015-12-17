# Netty Point to Point Communication Bandwidth & Latency Benchmark
It's inspired by pt2pt benchmark in [osu-micro-benchmarks](http://mvapich.cse.ohio-state.edu/benchmarks/). We give a table to describe the bandwidth and latency under the diffrent communicating block size from 1B to 4M. Now the supported netty version is 4.x.

## Bandwidth
Process a chunk of async comm operations in a step, then wait the async operations' ending. Do a series of such above steps to calculated the average communicating throughput under the given comm size.


## Latency
Process a series sync comm under the given comm size to calculate the average latency in a sync send-recv operation.

