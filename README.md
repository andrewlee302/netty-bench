# Netty Pt2pt Communication Bandwidth & Latency Benchmark

It's inspired by pt2pt(point to point) benchmark in [osu-micro-benchmarks](http://mvapich.cse.ohio-state.edu/benchmarks/). We give a table to describe the bandwidth and latency under the diffrent communicating block size from 1B to 4M. Now the supported netty version is 4.x.

The usage is like this:

	 bench.Bandwidth serverIP serverPort isServer(true or false)

So you will start server firstly with `true` on `isServer` flag, then start the client with `false` on it.

## Bandwidth

Process a chunk of async comm operations in a step, then wait the async operations' ending. Do a series of such above steps to calculated the average communicating throughput under the given comm size.
On server:

	$ java -cp netty-bench.jar:lib/netty-all-4.0.25.Final.jar bench.Bandwidth localhost 10001 true

On client:

	$ java -cp netty-bench.jar:lib/netty-all-4.0.25.Final.jar bench.Bandwidth serverIP 10001 false


## Latency

Process a series sync comm under the given comm size to calculate the average latency in a sync send-recv operation.
On server:

	$ java -cp netty-bench.jar:lib/netty-all-4.0.25.Final.jar bench.Latency localhost 10001 true

On client:

	$ java -cp netty-bench.jar:lib/netty-all-4.0.25.Final.jar bench.Latency serverIP 10001 false
  

