# Swift-Like Congestion Control Conceptual Simulation in CloudSim

This project presents a conceptual simulation of a Swift-inspired congestion control approach in the CloudSim environment. The goal is to evaluate how delay-aware scheduling and congestion-control-inspired behavior can improve datacenter performance metrics at a high level.

Since CloudSim does not simulate packet-level TCP behavior, this project does not implement the original Swift protocol exactly. Instead, it provides a conceptual model to study the overall system-level impact of reduced delay and improved task scheduling.

## Project Objectives

- Simulate a Swift-like conceptual congestion control behavior in CloudSim
- Compare the proposed model with a baseline scheduling scenario
- Evaluate the impact on key performance metrics
- Analyze the effectiveness of delay-aware behavior in datacenter task execution

## Scenarios

The project evaluates two execution scenarios:

- `BASELINE`
- `SWIFT_LIKE`

Both scenarios are executed under the same workload conditions to ensure a fair comparison.

## Performance Metrics

The following metrics are used for evaluation:

- `NumberOfCloudlets`
- `TotalCPUTime`
- `AverageFinishTime`
- `Makespan`
- `Throughput`

## Results Summary

The `SWIFT_LIKE` scenario demonstrated better performance than `BASELINE`:

- Average Finish Time decreased by about `34%`
- Makespan decreased by about `36%`
- Throughput increased by about `57%`
- Total CPU Time decreased significantly

### Numerical Comparison

| Metric | BASELINE | SWIFT_LIKE |
|--------|----------|------------|
| NumberOfCloudlets | 20 | 20 |
| TotalCPUTime | 2889.9780 | 1905.8208 |
| AverageFinishTime | 144.5089 | 95.3010 |
| Makespan | 220.0000 | 140.0000 |
| Throughput | 0.090905 | 0.142847 |

## Project Structure
```text
project/
├── src/
│   └── SwiftLikeSimulation.java
├── outputs/
│   ├── metrics_comparison.csv
│   ├── summary_report.txt
│   ├── raw_metrics_chart.png
│   └── throughput_chart.png
├── docs/
│   └── report.pdf
├── README.md
└── .gitignore

## How to Compile and Run

Compile the Java source file:

bash
javac -cp ".;lib/*" src/SwiftLikeSimulation.java

Run the simulation:

bash
java -cp ".;lib/*;src" SwiftLikeSimulation

Note: Update the classpath based on your local CloudSim library location and project structure.

## Output Files

The project generates several outputs for analysis and reporting:

- `metrics_comparison.csv`  
  Structured numerical comparison of the evaluated metrics

- `summary_report.txt`  
  Text summary of the simulation results

- `raw_metrics_chart.png`  
  Visual comparison of key timing and CPU metrics

- `throughput_chart.png`  
  Throughput comparison between the two scenarios

## Why CloudSim?

CloudSim was used because it provides a flexible and lightweight environment for modeling datacenter resources, virtual machines, cloudlets, and scheduling policies without requiring a real cloud infrastructure.

It is especially suitable for this project because:

- it allows controlled comparison between different scheduling scenarios
- it provides measurable performance outputs
- it supports rapid experimentation and analysis
- it is appropriate for conceptual system-level simulation

## Limitations

This project is a conceptual simulation and not a full packet-level or TCP-level implementation of the original Swift congestion control algorithm.

Therefore:

- RTT is not modeled at the real transport protocol level
- network packet behavior is abstracted
- the results should be interpreted as system-level conceptual findings

## Future Work

Possible future extensions include:

- implementing more detailed delay models
- testing larger workloads and more VMs
- using network-focused simulators such as ns-3
- extending the model to multi-datacenter scenarios
- combining scheduling logic with more realistic congestion indicators

## Author

- Your Name

## License

This project is for academic and educational purposes.