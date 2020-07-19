import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dependencies.Config;
import dependencies.Emailer;
import dependencies.Logger;
import dependencies.Project;

/*
 * Total time 70'
 * Not happy with the end result.
 * There are still a lot of IFs. Technically I could remove them (using True/False functions)
 * but that feels wrong die its complexity. This is a flow with decisions. 
 */

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
        // TODO would move steps to constructor, but tests are set up differently
        BuildSteps steps = new BuildSteps(config, emailer, log);
        BuildResults results = new BuildResults();
        steps.progress(project, results);
    }

    static class BuildResults {
        private final Map<String, Boolean> results = new HashMap<>();

        public void reportSuccess(String key, boolean testsPassed) {
            results.put(key, testsPassed);
        }

        public boolean isSuccess(String key) {
            return results.containsKey(key) && results.get(key);
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

            boolean testsPassed = "success".equals(project.runTests());
            logIt(testsPassed);
            results.reportSuccess(SUCCESS_KEY, testsPassed);
        }

        private void logIt(boolean testsSuccessful) {
            if (testsSuccessful) {
                log.info("Tests passed");
            } else {
                log.error("Tests failed");
            }
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

            boolean deploySuccessful = "success".equals(project.deploy());
            logIt(deploySuccessful);
            results.reportSuccess(SUCCESS_KEY, deploySuccessful);
        }

        private void logIt(boolean deploySuccessful) {
            if (deploySuccessful) {
                log.info("Deployment successful");
            } else {
                log.error("Deployment failed");
            }
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
            log.info("Sending email");

            boolean testsPassed = results.isSuccess("testsPassed");
            if (!testsPassed) {
                emailer.send("Tests failed");
                return;
            }

            boolean deploySuccessful = results.isSuccess("deploySuccessful");
            mailIt(deploySuccessful);
        }

        private void mailIt(boolean deploySuccessful) {
            if (deploySuccessful) {
                emailer.send("Deployment completed successfully");
            } else {
                emailer.send("Deployment failed");
            }
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
