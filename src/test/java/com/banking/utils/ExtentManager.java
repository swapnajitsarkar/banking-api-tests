package com.banking.utils;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Thread-safe Extent Reports manager.
 * One ExtentTest per thread → safe for parallel test runs.
 */
public class ExtentManager {

    private static ExtentReports extent;
    private static final ThreadLocal<ExtentTest> test = new ThreadLocal<>();

    private ExtentManager() {}

    public static synchronized ExtentReports getInstance() {
        if (extent == null) {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String reportPath = System.getProperty("user.dir")
                    + "/reports/APITestReport_" + timestamp + ".html";

            ExtentSparkReporter spark = new ExtentSparkReporter(reportPath);
            spark.config().setTheme(Theme.DARK);
            spark.config().setDocumentTitle("Banking API Test Report");
            spark.config().setReportName("Digital Banking System — REST Assured Suite");
            spark.config().setTimeStampFormat("dd MMM yyyy HH:mm:ss");
            spark.config().setCss(
                ".badge-primary { background: #1a73e8 !important; }"
            );

            extent = new ExtentReports();
            extent.attachReporter(spark);
            extent.setSystemInfo("Framework",   "Rest Assured 5 + TestNG");
            extent.setSystemInfo("Environment", System.getProperty("base.url",
                                               System.getenv("BASE_URL") != null
                                               ? System.getenv("BASE_URL")
                                               : "localhost:8080"));
            extent.setSystemInfo("Author",      "Swapnajit");
            extent.setSystemInfo("Java Version", System.getProperty("java.version"));
        }
        return extent;
    }

    public static void setTest(ExtentTest t)  { test.set(t); }
    public static ExtentTest  getTest()        { return test.get(); }
    public static void        removeTest()     { test.remove(); }
    public static void        flush()          { if (extent != null) extent.flush(); }
}
