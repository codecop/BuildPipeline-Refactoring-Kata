import java.util.HashMap;
import java.util.Map;

import dependencies.Config;
import dependencies.Emailer;
import dependencies.Logger;
import dependencies.Project;

public class Pipeline {
    private final Config config;
    private final Emailer emailer;
    private final Logger log;

    public Pipeline(Config config, Emailer emailer, Logger log) {
        this.config = config;
        this.emailer = emailer;
        this.log = log;
    }

    public void run(Project project) {
        Map<String, Boolean> results = new HashMap<>();
        new TestStep().handleTests(project, results);
        new DeployStep().handleDeployment(project, results);
        new EmailStep().handleEmail(project, results);
    }

    class TestStep {

        public void handleTests(Project project, Map<String, Boolean> results) {
            boolean testsPassed;
            if (project.hasTests()) {
                testsPassed = runTests(project);
            } else {
                log.info("No tests");
                testsPassed = true;
            }
            results.put("testsPassed", testsPassed);
        }

        private boolean runTests(Project project) {
            boolean testsPassed;
            if ("success".equals(project.runTests())) {
                log.info("Tests passed");
                testsPassed = true;
            } else {
                log.error("Tests failed");
                testsPassed = false;
            }
            return testsPassed;
        }

    }

    class DeployStep {

        public void handleDeployment(Project project, Map<String, Boolean> results) {
            boolean deploySuccessful;
            if (results.get("testsPassed")) {
                deploySuccessful = deploy(project);
            } else {
                deploySuccessful = false;
            }
            results.put("deploySuccessful", deploySuccessful);
        }

        private boolean deploy(Project project) {
            boolean deploySuccessful;
            if ("success".equals(project.deploy())) {
                log.info("Deployment successful");
                deploySuccessful = true;
            } else {
                log.error("Deployment failed");
                deploySuccessful = false;
            }
            return deploySuccessful;
        }

    }

    class EmailStep {

        public void handleEmail(Project project, Map<String, Boolean> results) {
            if (config.sendEmailSummary()) {
                sendEmails(results.get("testsPassed"), results.get("deploySuccessful"));
            } else {
                log.info("Email disabled");
            }
        }

        private void sendEmails(boolean testsPassed, boolean deploySuccessful) {
            log.info("Sending email");
            if (testsPassed) {
                if (deploySuccessful) {
                    emailer.send("Deployment completed successfully");
                } else {
                    emailer.send("Deployment failed");
                }
            } else {
                emailer.send("Tests failed");
            }
        }
    }
}
