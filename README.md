# TestWheel Automation Plugin

## Introduction

The [TestWheel](https://www.testwheel.com/) Automation Plugin is a powerful plugin designed to integrate seamlessly with DevOps tools for continuous integration and deployment (CI/CD). This plugin automates the execution of TestWheel's comprehensive testing frameworks within your CI/CD pipelines post-deployment, ensuring reliable testing throughout your development lifecycle.

## Getting Started

To get started with the TestWheel Automation Plugin, simply install the plugin from the Jenkins Marketplace and ensure you integrate the plugin with your CI/CD pipeline.

## Prerequisites

To effectively utilize the TestWheel Automation Plugin, please ensure you have the following:

- An existing Jenkins CI Server.
- A required TestWheel account for a seamless automation process. [Register](https://app.testwheel.com/registration) or [Login](https://app.testwheel.com/login) with TestWheel using the mentioned link.
- Ensure you have registered your application in TestWheel and obtained the Secure `ApiKey` & `PrjctKey` for the application to perform post-deployment test automation using the plugin.

## Installation and Setup

1. Go to the Jenkins Marketplace running by default locally.
2. Open the **Manage Jenkins** page and click **Manage Plugins**.
3. On the Plugin Manager page, click **Available Plugins**.
4. Search for **TestWheel Automation Plugin** and install it.
5. After successful installation, ensure you have logged in with TestWheel Automation.
6. Create a new task in your CI/CD pipeline and provide the application's `ApiKey` & `PrjctKey` obtained from the web portal to trigger the test.

### Sample Jenkins Pipeline Script

Below is a sample pipeline script demonstrating how to use the **TestWheel Automation Plugin**.  

> **Note:**  
> - Replace `<YOUR_STAGE_NAME>`, `<YOUR_API_KEY>`, and `<YOUR_PROJECT_KEY>` with the actual values.  
> - The stage name is user-defined and can be customized as per your CI/CD workflow.

```groovy
pipeline {
    agent any
    stages {
        stage('<YOUR_STAGE_NAME>') {
            steps {
                testwheelTrigger(apiKey: '<YOUR_API_KEY>', prjctKey: '<YOUR_PROJECT_KEY>')
            }
        }
    }
}
```

## Build and Test

To execute tests in your CI/CD pipeline post-deployment stage, integrate the Application ApiKey & PrjctKey into your DevOps tool. This integration will trigger the tests and generate a report. If the application passes the tests, the pipeline will proceed to the next stage. Conversely, if the application fails, the pipeline will terminate at that point.

## Contribute

TestWheel accepts feedback, whether positive or negative. Users can submit their concerns through the [Contact Us](https://app.testwheel.com/contact-us) link.
