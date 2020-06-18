import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
        BuildResults results = new BuildResults();
        List<Step> steps = Arrays.asList(
                new TestStep(log),
                new DeployStep(log),
                new EmailStep(config, emailer, log)
                );
        steps.get(0).handle(project, results);
        steps.get(1).handle(project, results);
        steps.get(2).handle(project, results);
    }

    static class BuildResults {
        private final Map<String, Boolean> results = new HashMap<>();

        public void reportSuccess(String key, boolean testsPassed) {
            results.put(key, testsPassed);
        }

        public boolean isSucces(String key) {
            return results.containsKey(key) && results.get(key);
        }

    }

    interface Step {

        void handle(Project project, BuildResults results);

    }

    static class TestStep implements Step {

        private final Logger log;

        public TestStep(Logger log) {
            this.log = log;
        }

        @Override
        public void handle(Project project, BuildResults results) {
            boolean testsPassed;
            if (project.hasTests()) {
                testsPassed = runTests(project);
            } else {
                log.info("No tests");
                testsPassed = true;
            }
            results.reportSuccess("testsPassed", testsPassed);
        }

        private boolean runTests(Project project) {
            if ("success".equals(project.runTests())) {
                log.info("Tests passed");
                return true;
            }

            log.error("Tests failed");
            return false;
        }

    }

    static class DeployStep implements Step {

        private final Logger log;

        public DeployStep(Logger log) {
            this.log = log;
        }

        @Override
        public void handle(Project project, BuildResults results) {
            boolean deploySuccessful;
            if (results.isSucces("testsPassed")) {
                deploySuccessful = deploy(project);
            } else {
                deploySuccessful = false;
            }
            results.reportSuccess("deploySuccessful", deploySuccessful);
        }

        private boolean deploy(Project project) {
            if ("success".equals(project.deploy())) {
                log.info("Deployment successful");
                return true;
            }

            log.error("Deployment failed");
            return false;
        }

    }

    static class EmailStep implements Step {

        private final Config config;
        private final Emailer emailer;
        private final Logger log;

        public EmailStep(Config config, Emailer emailer, Logger log) {
            this.config = config;
            this.emailer = emailer;
            this.log = log;
        }

        @Override
        public void handle(@SuppressWarnings("unused") Project project, BuildResults results) {
            if (config.sendEmailSummary()) {
                sendEmails(results.isSucces("testsPassed"), results.isSucces("deploySuccessful"));
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
