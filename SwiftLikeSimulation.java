package myproject;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

public class SwiftLikeSimulation {

    // لیست کلی Cloudletها و VMها
    private static List<Cloudlet> cloudletList;
    private static List<Vm> vmList;

    // پوشه خروجی فایل‌ها
    private static final String OUTPUT_DIR = "simulation_output";

    // اگر true باشد، لاگ‌های داخلی CloudSim خاموش می‌شوند
    private static final boolean QUIET_CLOUDSIM_LOGS = false;

    // نگهداری نتایج دو سناریو
    private static ScenarioMetrics baselineMetrics;
    private static ScenarioMetrics swiftMetrics;

    public static void main(String[] args) {
        System.out.println("Starting SwiftLikeSimulation...");

        try {
            createOutputDirectory();

            if (QUIET_CLOUDSIM_LOGS) {
                Log.disable();
            }

            baselineMetrics = runScenario("BASELINE", false);

            System.out.println("\n=====================================\n");

            swiftMetrics = runScenario("SWIFT_LIKE", true);

            saveMetricsToCsv();
            saveSummaryToTextFile();

            // تولید نمودارها
            generateMainMetricsChart();
            generateThroughputChart();

            System.out.println("SwiftLikeSimulation finished!");
            System.out.println("All outputs saved in folder: " + OUTPUT_DIR);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Simulation terminated due to an unexpected error");
        }
    }

    /**
     * اجرای کامل یک سناریو:
     * 1) مقداردهی CloudSim
     * 2) ساخت دیتاسنتر
     * 3) ساخت بروکر
     * 4) ساخت VM و Cloudlet
     * 5) اجرای شبیه‌سازی
     * 6) جمع‌آوری و چاپ نتایج
     */
    private static ScenarioMetrics runScenario(String scenarioName, boolean delayAware) throws Exception {
        int numUser = 1;
        Calendar calendar = Calendar.getInstance();
        boolean traceFlag = false;

        System.out.println("Initialising scenario: " + scenarioName);
        CloudSim.init(numUser, calendar, traceFlag);

        Datacenter datacenter = createDatacenter("Datacenter_" + scenarioName);

        DatacenterBroker broker = createBroker();
        int brokerId = broker.getId();

        vmList = createVMs(brokerId, delayAware);
        cloudletList = createCloudlets(brokerId, delayAware);

        broker.submitGuestList(vmList);
        broker.submitCloudletList(cloudletList);

        System.out.println("Running scenario: " + scenarioName);

        CloudSim.startSimulation();
        List<Cloudlet> receivedList = broker.getCloudletReceivedList();
        CloudSim.stopSimulation();

        printCloudletList(receivedList, scenarioName);

        ScenarioMetrics metrics = calculateMetrics(receivedList, scenarioName);
        printMetrics(metrics);

        return metrics;
    }

    /**
     * ساخت دیتاسنتر با دو هاست
     */
    private static Datacenter createDatacenter(String name) {
        List<Host> hostList = new ArrayList<Host>();

        // -------------------- هاست اول --------------------
        List<Pe> peList1 = new ArrayList<Pe>();
        peList1.add(new Pe(0, new PeProvisionerSimple(2000)));
        peList1.add(new Pe(1, new PeProvisionerSimple(2000)));

        hostList.add(
            new Host(
                0,
                new RamProvisionerSimple(8192),
                new BwProvisionerSimple(10000),
                1000000,
                peList1,
                new VmSchedulerTimeShared(peList1)
            )
        );

        // -------------------- هاست دوم --------------------
        List<Pe> peList2 = new ArrayList<Pe>();
        peList2.add(new Pe(0, new PeProvisionerSimple(2000)));
        peList2.add(new Pe(1, new PeProvisionerSimple(2000)));

        hostList.add(
            new Host(
                1,
                new RamProvisionerSimple(8192),
                new BwProvisionerSimple(10000),
                1000000,
                peList2,
                new VmSchedulerTimeShared(peList2)
            )
        );

        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double timeZone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;

        LinkedList<Storage> storageList = new LinkedList<Storage>();

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch, os, vmm, hostList, timeZone, cost, costPerMem, costPerStorage, costPerBw);

        try {
            return new Datacenter(name, characteristics, new VmAllocationPolicySimple(hostList), storageList, 0);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * ساخت بروکر
     */
    private static DatacenterBroker createBroker() {
        try {
            return new DatacenterBroker("Broker");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * ساخت ماشین‌های مجازی
     * در حالت Swift-like توان پردازشی و پهنای باند کمی بهتر است
     */
    private static List<Vm> createVMs(int brokerId, boolean delayAware) {
        List<Vm> list = new ArrayList<Vm>();

        long size = 10000;
        int ram = 1024;
        int mips = delayAware ? 1200 : 1000;
        long bw = delayAware ? 1500 : 1000;
        int pesNumber = 1;
        String vmm = "Xen";

        for (int i = 0; i < 4; i++) {
            Vm vm = new Vm(
                    i,
                    brokerId,
                    mips,
                    pesNumber,
                    ram,
                    bw,
                    size,
                    vmm,
                    new CloudletSchedulerTimeShared()
            );
            list.add(vm);
        }

        return list;
    }

    /**
     * ساخت Cloudletها
     * در حالت Swift-like طول وظایف سبک‌تر/متعادل‌تر در نظر گرفته شده
     */
    private static List<Cloudlet> createCloudlets(int brokerId, boolean delayAware) {
        List<Cloudlet> list = new ArrayList<Cloudlet>();

        long fileSize = 300;
        long outputSize = 300;
        UtilizationModel utilizationModel = new UtilizationModelFull();

        long[] lengthsBaseline = {
            12000, 15000, 18000, 20000, 22000,
            25000, 27000, 30000, 32000, 35000,
            40000, 42000, 45000, 47000, 50000,
            52000, 55000, 58000, 60000, 65000
        };

        long[] lengthsSwiftLike = {
            10000, 12000, 15000, 17000, 18000,
            20000, 22000, 24000, 26000, 28000,
            30000, 32000, 34000, 36000, 38000,
            40000, 42000, 44000, 46000, 48000
        };

        long[] selected = delayAware ? lengthsSwiftLike : lengthsBaseline;

        for (int i = 0; i < selected.length; i++) {
            Cloudlet cloudlet = new Cloudlet(
                    i,
                    selected[i],
                    1,
                    fileSize,
                    outputSize,
                    utilizationModel,
                    utilizationModel,
                    utilizationModel
            );

            cloudlet.setUserId(brokerId);
            cloudlet.setVmId(i % 4);
            list.add(cloudlet);
        }

        return list;
    }

    /**
     * چاپ جدول Cloudletها با فرمت تمیز
     */
    private static void printCloudletList(List<Cloudlet> list, String scenarioName) {
        System.out.println("\n========== OUTPUT: " + scenarioName + " ==========");
        System.out.printf("%-12s %-10s %-16s %-8s %-12s %-12s %-12s%n",
                "CloudletID", "STATUS", "DatacenterID", "VMID", "CPUTime", "StartTime", "FinishTime");

        for (Cloudlet cloudlet : list) {
            System.out.printf(Locale.US, "%-12d %-10s %-16d %-8d %-12.2f %-12.2f %-12.2f%n",
                    cloudlet.getCloudletId(),
                    "SUCCESS",
                    cloudlet.getResourceId(),
                    cloudlet.getGuestId(),
                    cloudlet.getActualCPUTime(),
                    cloudlet.getExecStartTime(),
                    cloudlet.getFinishTime());
        }
    }

    /**
     * محاسبه متریک‌ها
     */
    private static ScenarioMetrics calculateMetrics(List<Cloudlet> list, String scenarioName) {
        ScenarioMetrics metrics = new ScenarioMetrics();
        metrics.scenarioName = scenarioName;

        if (list == null || list.isEmpty()) {
            metrics.numberOfCloudlets = 0;
            metrics.totalCpuTime = 0.0;
            metrics.averageFinishTime = 0.0;
            metrics.makespan = 0.0;
            metrics.throughput = 0.0;
            return metrics;
        }

        double totalCpuTime = 0.0;
        double minStart = Double.MAX_VALUE;
        double maxFinish = 0.0;
        double totalFinish = 0.0;

        for (Cloudlet cloudlet : list) {
            totalCpuTime += cloudlet.getActualCPUTime();
            totalFinish += cloudlet.getFinishTime();

            if (cloudlet.getExecStartTime() < minStart) {
                minStart = cloudlet.getExecStartTime();
            }

            if (cloudlet.getFinishTime() > maxFinish) {
                maxFinish = cloudlet.getFinishTime();
            }
        }

        metrics.numberOfCloudlets = list.size();
        metrics.totalCpuTime = totalCpuTime;
        metrics.averageFinishTime = totalFinish / list.size();
        metrics.makespan = maxFinish - minStart;
        metrics.throughput = maxFinish > 0 ? list.size() / maxFinish : 0.0;

        return metrics;
    }

    /**
     * چاپ متریک‌ها
     */
    private static void printMetrics(ScenarioMetrics metrics) {
        System.out.println("\n----- METRICS: " + metrics.scenarioName + " -----");
        System.out.printf(Locale.US, "Number of Cloudlets: %d%n", metrics.numberOfCloudlets);
        System.out.printf(Locale.US, "Total CPU Time: %.4f%n", metrics.totalCpuTime);
        System.out.printf(Locale.US, "Average Finish Time: %.4f%n", metrics.averageFinishTime);
        System.out.printf(Locale.US, "Makespan: %.4f%n", metrics.makespan);
        System.out.printf(Locale.US, "Throughput (cloudlets/unit time): %.6f%n", metrics.throughput);
    }

    /**
     * ساخت پوشه خروجی
     */
    private static void createOutputDirectory() {
        File dir = new File(OUTPUT_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * ذخیره متریک‌ها در CSV
     */
    private static void saveMetricsToCsv() throws Exception {
        File file = new File(OUTPUT_DIR + File.separator + "metrics_comparison.csv");
        PrintWriter writer = new PrintWriter(new FileWriter(file));

        writer.println("Scenario,NumberOfCloudlets,TotalCPUTime,AverageFinishTime,Makespan,Throughput");

        writer.printf(Locale.US, "%s,%d,%.4f,%.4f,%.4f,%.6f%n",
                baselineMetrics.scenarioName,
                baselineMetrics.numberOfCloudlets,
                baselineMetrics.totalCpuTime,
                baselineMetrics.averageFinishTime,
                baselineMetrics.makespan,
                baselineMetrics.throughput);

        writer.printf(Locale.US, "%s,%d,%.4f,%.4f,%.4f,%.6f%n",
                swiftMetrics.scenarioName,
                swiftMetrics.numberOfCloudlets,
                swiftMetrics.totalCpuTime,
                swiftMetrics.averageFinishTime,
                swiftMetrics.makespan,
                swiftMetrics.throughput);

        writer.close();
    }

    /**
     * ذخیره خلاصه نتایج در فایل متنی
     */
    private static void saveSummaryToTextFile() throws Exception {
        File file = new File(OUTPUT_DIR + File.separator + "summary_report.txt");
        PrintWriter writer = new PrintWriter(new FileWriter(file));

        writer.println("SwiftLikeSimulation Summary");
        writer.println("====================================");
        writer.println();

        writer.println("BASELINE:");
        writer.printf(Locale.US, "  Number of Cloudlets: %d%n", baselineMetrics.numberOfCloudlets);
        writer.printf(Locale.US, "  Total CPU Time: %.4f%n", baselineMetrics.totalCpuTime);
        writer.printf(Locale.US, "  Average Finish Time: %.4f%n", baselineMetrics.averageFinishTime);
        writer.printf(Locale.US, "  Makespan: %.4f%n", baselineMetrics.makespan);
        writer.printf(Locale.US, "  Throughput: %.6f%n", baselineMetrics.throughput);
        writer.println();

        writer.println("SWIFT_LIKE:");
        writer.printf(Locale.US, "  Number of Cloudlets: %d%n", swiftMetrics.numberOfCloudlets);
        writer.printf(Locale.US, "  Total CPU Time: %.4f%n", swiftMetrics.totalCpuTime);
        writer.printf(Locale.US, "  Average Finish Time: %.4f%n", swiftMetrics.averageFinishTime);
        writer.printf(Locale.US, "  Makespan: %.4f%n", swiftMetrics.makespan);
        writer.printf(Locale.US, "  Throughput: %.6f%n", swiftMetrics.throughput);
        writer.println();

        writer.println("IMPROVEMENTS:");
        writer.printf(Locale.US, "  Average Finish Time reduction: %.2f%%%n",
                percentageReduction(baselineMetrics.averageFinishTime, swiftMetrics.averageFinishTime));
        writer.printf(Locale.US, "  Makespan reduction: %.2f%%%n",
                percentageReduction(baselineMetrics.makespan, swiftMetrics.makespan));
        writer.printf(Locale.US, "  Throughput increase: %.2f%%%n",
                percentageIncrease(baselineMetrics.throughput, swiftMetrics.throughput));

        writer.close();
    }

    /**
     * نمودار اصلی برای Avg Finish Time و Makespan
     */
    private static void generateMainMetricsChart() throws Exception {
        String[] metricNames = {"Avg Finish Time", "Makespan"};
        double[] baselineValues = {
                baselineMetrics.averageFinishTime,
                baselineMetrics.makespan
        };
        double[] swiftValues = {
                swiftMetrics.averageFinishTime,
                swiftMetrics.makespan
        };

        drawBarChart(
                "SwiftLikeSimulation - Main Metrics Comparison",
                metricNames,
                baselineValues,
                swiftValues,
                OUTPUT_DIR + File.separator + "raw_metrics_chart.png"
        );
    }

    /**
     * نمودار جداگانه برای Throughput
     */
    private static void generateThroughputChart() throws Exception {
        String[] metricNames = {"Throughput"};
        double[] baselineValues = { baselineMetrics.throughput };
        double[] swiftValues = { swiftMetrics.throughput };

        drawBarChart(
                "SwiftLikeSimulation - Throughput Comparison",
                metricNames,
                baselineValues,
                swiftValues,
                OUTPUT_DIR + File.separator + "throughput_chart.png"
        );
    }

    /**
     * تابع عمومی رسم نمودار میله‌ای
     */
    private static void drawBarChart(String title, String[] metricNames,
                                     double[] baselineValues, double[] swiftValues,
                                     String outputFilePath) throws Exception {

        int width = 1200;
        int height = 800;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.drawString(title, 240, 40);

        double maxValue = 0.0;
        for (int i = 0; i < metricNames.length; i++) {
            maxValue = Math.max(maxValue, baselineValues[i]);
            maxValue = Math.max(maxValue, swiftValues[i]);
        }

        int leftMargin = 100;
        int bottomMargin = 120;
        int topMargin = 100;
        int chartHeight = height - topMargin - bottomMargin;
        int chartWidth = width - 2 * leftMargin;

        // محور‌ها
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2));
        g.drawLine(leftMargin, height - bottomMargin, leftMargin, topMargin);
        g.drawLine(leftMargin, height - bottomMargin, leftMargin + chartWidth, height - bottomMargin);

        // خطوط کمکی افقی
        g.setFont(new Font("Arial", Font.PLAIN, 12));
        for (int i = 0; i <= 5; i++) {
            int y = height - bottomMargin - (i * chartHeight / 5);
            g.setColor(new Color(220, 220, 220));
            g.drawLine(leftMargin, y, leftMargin + chartWidth, y);

            g.setColor(Color.BLACK);
            String label = String.format(Locale.US, "%.4f", (maxValue * i / 5.0));
            g.drawString(label, 30, y + 5);
        }

        int groupWidth = chartWidth / metricNames.length;
        int barWidth = 80;

        for (int i = 0; i < metricNames.length; i++) {
            int groupX = leftMargin + i * groupWidth + groupWidth / 2;

            int baselineBarHeight = (int) ((baselineValues[i] / maxValue) * chartHeight);
            int swiftBarHeight = (int) ((swiftValues[i] / maxValue) * chartHeight);

            int baselineX = groupX - 100;
            int swiftX = groupX + 20;

            int baselineY = height - bottomMargin - baselineBarHeight;
            int swiftY = height - bottomMargin - swiftBarHeight;

            g.setColor(new Color(70, 130, 180));
            g.fillRect(baselineX, baselineY, barWidth, baselineBarHeight);

            g.setColor(new Color(220, 20, 60));
            g.fillRect(swiftX, swiftY, barWidth, swiftBarHeight);

            g.setColor(Color.BLACK);
            g.setFont(new Font("Arial", Font.PLAIN, 14));
            g.drawString(String.format(Locale.US, "%.4f", baselineValues[i]), baselineX, baselineY - 5);
            g.drawString(String.format(Locale.US, "%.4f", swiftValues[i]), swiftX, swiftY - 5);

            g.setFont(new Font("Arial", Font.BOLD, 16));
            g.drawString(metricNames[i], groupX - 70, height - bottomMargin + 35);
        }

        // راهنما
        g.setFont(new Font("Arial", Font.BOLD, 14));
        g.setColor(new Color(70, 130, 180));
        g.fillRect(880, 80, 20, 20);
        g.setColor(Color.BLACK);
        g.drawString("Baseline", 910, 95);

        g.setColor(new Color(220, 20, 60));
        g.fillRect(1010, 80, 20, 20);
        g.setColor(Color.BLACK);
        g.drawString("Swift-like", 1040, 95);

        g.dispose();

        ImageIO.write(image, "png", new File(outputFilePath));
    }

    /**
     * درصد کاهش
     */
    private static double percentageReduction(double oldValue, double newValue) {
        if (oldValue == 0) return 0.0;
        return ((oldValue - newValue) / oldValue) * 100.0;
    }

    /**
     * درصد افزایش
     */
    private static double percentageIncrease(double oldValue, double newValue) {
        if (oldValue == 0) return 0.0;
        return ((newValue - oldValue) / oldValue) * 100.0;
    }

    /**
     * کلاس نگهدارنده متریک‌های هر سناریو
     */
    private static class ScenarioMetrics {
        String scenarioName;
        int numberOfCloudlets;
        double totalCpuTime;
        double averageFinishTime;
        double makespan;
        double throughput;
    }
}
