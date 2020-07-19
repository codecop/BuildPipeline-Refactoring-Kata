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
        BuildSteps steps = new BuildSteps(config, emailer, log);
        BuildResults results = new BuildResults();
        steps.progress(project, results);
    }

    static class Success {
        public final boolean wasSuccess;

        public Success(boolean wasSuccess) {
            this.wasSuccess = wasSuccess;
        }

        public void then(Runnable onSuccess, Runnable onFailure) {
            if (wasSuccess) {
                onSuccess.run();
            } else {
                onFailure.run();
            }
        }

        public void then(Runnable onSuccess) {
            then(onSuccess, () -> {
                /* do nothing */ });
        }

    }

    static class BuildResults {
        private final Map<String, Boolean> results = new HashMap<>();

        public void reportSuccess(String key, boolean testsPassed) {
            results.put(key, testsPassed);
        }

        public boolean isSuccess(String key) {
            return results.containsKey(key) && results.get(key);
        }

        public Success onSuccess(String key) {
            return new Success(isSuccess(key));
        }

    }

    static class BuildSteps extends CompositeBuildSteps {

        public BuildSteps(Config config, Emailer emailer, Logger log) {
            super(//
                    new TestStep(log), //
                    new DeployStep(log), //
                    config.sendEmailSummary() ? new EmailStep(emailer, log) : new DisabledEmailStep(log) //
            );
        }

    }

    static class CompositeBuildSteps implements BuildStep {

        private final List<BuildStep> steps;

        public CompositeBuildSteps(BuildStep... steps) {
            this.steps = Arrays.asList(steps);
        }

        @Override
        public void progress(Project project, BuildResults results) {
            steps.stream().forEach(step -> step.progress(project, results));
        }

    }

    interface BuildStep {

        void progress(Project project, BuildResults results);

    }

    static class TestStep implements BuildStep {

        private static final String SUCCESS_KEY = "testsPassed";

        private final Logger log;

        public TestStep(Logger log) {
            this.log = log;
        }

        @Override
        public void progress(Project project, BuildResults results) {
            if (!project.hasTests()) {
                log.info("No tests");
                results.reportSuccess(SUCCESS_KEY, true);
                return;
            }

            boolean testsPassed = runTests(project);
            results.reportSuccess(SUCCESS_KEY, testsPassed);
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

    static class DeployStep implements BuildStep {

        private static final String SUCCESS_KEY = "deploySuccessful";

        private final Logger log;

        public DeployStep(Logger log) {
            this.log = log;
        }

        @Override
        public void progress(Project project, BuildResults results) {
            if (!results.isSuccess("testsPassed")) {
                return;
            }

            boolean deploySuccessful = deploy(project);
            results.reportSuccess(SUCCESS_KEY, deploySuccessful);
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

    static class EmailStep implements BuildStep {

        private final Emailer emailer;
        private final Logger log;

        public EmailStep(Emailer emailer, Logger log) {
            this.emailer = emailer;
            this.log = log;
        }

        @Override
        public void progress(@SuppressWarnings("unused") Project project, BuildResults results) {
            sendEmails(results.isSuccess("testsPassed"), results.isSuccess("deploySuccessful"));
        }

        private void sendEmails(boolean testsPassed, boolean deploySuccessful1) {
            log.info("Sending email");
            if (!testsPassed) {
                emailer.send("Tests failed");
                return;
            }

            Success onDeploySuccess = new Success(deploySuccessful1);
            onDeploySuccess.then(this::mailDeploySuccess, this::mailDeployFailed);
        }

        private void mailDeploySuccess() {
            emailer.send("Deployment completed successfully");
        }

        private void mailDeployFailed() {
            emailer.send("Deployment failed");
        }
    }

    static class DisabledEmailStep implements BuildStep {

        private final Logger log;

        public DisabledEmailStep(Logger log) {
            this.log = log;
        }

        @Override
        @SuppressWarnings("unused")
        public void progress(Project project, BuildResults results) {
            log.info("Email disabled");
        }

    }
}
