package gitlet;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/** Driver class for Gitlet, the tiny stupid version-control system.
 *  @author Aayush Gupta
 */
public class Main {

    /** List of all possible Gitlet commands. */
    private static final List<String> COMMANDS = Arrays.asList("init", "add",
            "commit", "rm", "log", "global-log", "find", "status", "checkout",
            "branch", "rm-branch", "reset", "merge", "diffs");
    /** Represents the repo object. */
    private static Repository _repo;

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND> .... */
    public static void main(String... args) throws IOException {
        File cwd = new File(System.getProperty("user.dir"));
        File repoDir = Utils.join(cwd, ".gitlet", "repository");
        if (repoDir.exists()) {
            _repo = Utils.readObject(repoDir, Repository.class);
        } else {
            _repo = new Repository();
        }

        if (args.length == 0) {
            Main.exitWithMessage("Please enter a command.");
        }
        if (args[0].equals("init")) {
            if (args.length == 1) {
                _repo.init();
            }
        } else if (!Utils.join(cwd, ".gitlet").exists()) {
            Main.exitWithMessage("Not in an initialized Gitlet directory.");
        } else if (args[0].equals("add")) {
            if (args.length == 2) {
                _repo.add(args[1]);
            }
        } else if (args[0].equals("rm")) {
            if (args.length == 2) {
                _repo.rm(args[1]);
            }
        } else if (args[0].equals("commit")) {
            if (args.length == 2) {
                _repo.commit(args[1]);
            }
        } else if (args[0].equals("log")) {
            if (args.length == 1) {
                _repo.log();
            }
        } else if (args[0].equals("checkout")) {
            if (args.length == 2) {
                _repo.checkoutBranch(args[1]);
            } else if (args.length == 3 && args[1].equals("--")) {
                _repo.checkoutFile(null, args[2]);
            } else if (args.length == 4 && args[2].equals("--")) {
                _repo.checkoutFile(args[1], args[3]);
            } else {
                Main.exitWithMessage("Incorrect operands.");
            }
        } else if (args[0].equals("global-log")) {
            if (args.length == 1) {
                _repo.globalLog();
            }
        } else if (args[0].equals("reset")) {
            if (args.length == 2) {
                _repo.reset(args[1]);
            }
        } else {
            mainPart2(args);
        }
        _repo.serialize();
    }

    private static void mainPart2(String[] args) throws IOException {
        if (args[0].equals("find")) {
            if (args.length == 2) {
                _repo.find(args[1]);
            }
        } else if (args[0].equals("status")) {
            if (args.length == 1) {
                _repo.status();
            }
        } else if (args[0].equals("branch")) {
            if (args.length == 2) {
                _repo.branch(args[1]);
            }
        } else if (args[0].equals("rm-branch")) {
            if (args.length == 2) {
                _repo.rmBranch(args[1]);
            }
        } else if (args[0].equals("merge")) {
            if (args.length == 2) {
                _repo.merge(args[1]);
            }
        } else if (args[0].equals("diff")) {
            if (args.length == 1) {
                _repo.diff(_repo.getCurBranch());
            } else if (args.length == 2) {
                _repo.diff(args[1]);
            } else {
                _repo.diff(args[1], args[2]);
            }
        } else if (!COMMANDS.contains(args[0])) {
            Main.exitWithMessage("No command with that name exists.");
        } else {
            Main.exitWithMessage("Incorrect operands.");
        }
    }

    public static void exitWithMessage(String message) {
        System.out.println(message);
        System.exit(0);
    }
}
