package com.banking.utils;

import com.aventstack.extentreports.Status;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

/**
 * TestNG listener — bridges TestNG events into Extent Reports.
 * Registered in testng.xml; no annotation needed on test classes.
 */
public class TestListener implements ITestListener {

    @Override
    public void onTestStart(ITestResult result) {
        var test = ExtentManager.getInstance()
                .createTest(
                        result.getMethod().getMethodName(),
                        result.getMethod().getDescription()
                );
        ExtentManager.setTest(test);
        test.info("Started: " + result.getMethod().getMethodName());
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        ExtentManager.getTest().log(Status.PASS, "PASSED");
    }

    @Override
    public void onTestFailure(ITestResult result) {
        ExtentManager.getTest()
                .log(Status.FAIL, "FAILED: " + result.getThrowable().getMessage())
                .fail(result.getThrowable());
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        ExtentManager.getTest().log(Status.SKIP, "SKIPPED");
    }

    @Override
    public void onFinish(ITestContext context) {
        ExtentManager.flush();
    }
}
