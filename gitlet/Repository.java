package gitlet;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import java.util.Collection;

public class Repository implements Serializable {

    /** Current working directory location. */
    private File _CWD = null;
    /** Main metadata folder. */
    private File _gitletFolder;
    /** Branches path. */
    private File _branches;
    /** Head pointer file path. */
    private File _head;
    /** Head file location. */
    private File _headFile;
    /** Blob storage directory. */
    private File _objects;
    /** Current branch name. */
    private String _currBranch;
    /** All branch pointers. */
    private File _refs;
    /** Ordered map between sha-1 code of commit and commit file location. */
    private HashMap<String, File> _commitTree;
    /** Adding staging object. */
    private Staging _addStage;
    /** Removal staging object. */
    private Staging _removeStage;
    /** Location where repository is serialized. */
    private File _repoSavePath;
    /** Indicates whether repo has already been initialized. */
    private boolean _initialized = false;
    /** Length of a SHA-1 hash. */
    static final int SHALENGTH = 40;

    public Repository() {

    }

    public void init() throws IOException {
        _CWD = new File(System.getProperty("user.dir"));
        _gitletFolder = new File(_CWD, ".gitlet");
        if (_gitletFolder.exists()) {
            Main.exitWithMessage("A Gitlet version-control system already"
                    + " exists in the current directory.");
        }
        _gitletFolder.mkdir();
        _objects = Utils.join(_gitletFolder, "objects");
        _objects.mkdir();
        _branches = Utils.join(_gitletFolder, "branches");
        _branches.mkdir();

        _refs = Utils.join(_gitletFolder, "refs");
        _refs.mkdir();
        _currBranch = "master";
        branch("master");

        _headFile = Utils.join(_gitletFolder, "HEAD");
        _headFile.createNewFile();

        _addStage = new Staging("add", _gitletFolder, _CWD);
        _removeStage = new Staging("remove", _gitletFolder, _CWD);
        _commitTree = new HashMap<>();

        commit("initial commit");
        updateHead("master");

        _repoSavePath = Utils.join(_gitletFolder, "repository");
    }
    /* Creates a new branch with name NAME. */
    public void branch(String name) throws IOException {
        if (Utils.join(_branches, name).exists()) {
            Main.exitWithMessage("A branch with that name already exists.");
        }
        Utils.join(_branches, name).mkdir();
        Utils.join(_refs, name).createNewFile();
        if (!name.equals("master")) {
            Utils.writeContents(Utils.join(_refs, name), lastCommitSha());
        }
    }
    public void commit(String message) throws IOException {
        commit(message, null);
    }
    /** Creates a new commit with the given MESSAGE and parent2 P2SHA. */
    public void commit(String message, String p2Sha) throws IOException {
        String parentSha;
        Commit parentCommit;
        Commit p2Commit = null;
        if (!_initialized) {
            parentSha = null;
            parentCommit = null;
            _initialized = true;
        } else {
            parentSha = lastCommitSha();
            parentCommit = getCommitFromSha(parentSha);
        }
        if (p2Sha != null) {
            p2Commit = getCommitFromSha(p2Sha);
        }
        Commit initial = new Commit(message, parentSha, parentCommit, p2Sha,
                p2Commit, this);
        String commitSha1 = Utils.sha1(Utils.serialize(initial));
        File commitPath = Utils.join(_branches, _currBranch, commitSha1);
        Utils.writeObject(commitPath, initial);
        updateBranchHead(_currBranch, commitSha1);
        _commitTree.put(commitSha1, commitPath);
    }
    /** Checks out FILENAME with SHA-1 ID. */
    public void checkoutFile(String id, String fileName) throws IOException {
        if (id == null) {
            id = lastCommitSha();
        }
        Commit chOutCommit = getCommitFromSha(id);
        if (chOutCommit == null) {
            Main.exitWithMessage("No commit with that id exists.");
        }
        String fileSha = chOutCommit.getShafromName(fileName);
        if (fileSha == null) {
            Main.exitWithMessage("File does not exist in that commit.");
        }
        File fileCommitPath = chOutCommit.getFileFromSha(fileSha);
        File workingDirPath = Utils.join(_CWD, fileName);
        workingDirPath.createNewFile();
        writeFromFile(fileCommitPath, workingDirPath);
    }

    /** Checks out all files tracked by the commit with SHA-1 SHA. Moves the
    branch's head to that commit node. */
    public void reset(String sha) throws IOException {
        checkoutBranchWithSha(sha);
        clearStaging();
        String newBranch = getCommitFromSha(sha).getBranch();
        updateBranchHead(newBranch, sha);
    }
    /** Updates BRANCHNAME's head commit with the new commit id SHA. */
    private void updateBranchHead(String branchName, String sha) {
        Utils.writeContents(Utils.join(_refs, branchName), sha);
    }
    /** Updates the head pointer file by chaning the current branch to
     * NEWBRANCH and writes path of current branch head to the head file. */
    public void updateHead(String newBranch) {
        _currBranch = newBranch;
        _head = Utils.join(_refs, _currBranch);
        Utils.writeContents(_headFile, _head.getPath());
    }
    /** Merge current branch with given branch with name BRANCHNAME. */
    public void merge(String branchName) throws IOException {
        checkBranchValid(branchName);
        if (_addStage.getStagedNameToPath().size() != 0
                || _removeStage.getStagedNameToPath().size() != 0) {
            Main.exitWithMessage("You have uncommitted changes.");
        } else if (branchName.equals(_currBranch)) {
            Main.exitWithMessage("Cannot merge a branch with itself.");
        }

        String curSha = lastCommitSha();
        Commit cur = getCommitFromSha(curSha);
        String givenSha = Utils.readContentsAsString(
                Utils.join(_refs, branchName));
        Commit given = getCommitFromSha(givenSha);
        String splitSha = findSplit(curSha, cur, givenSha, given);
        Commit split = getCommitFromSha(splitSha);

        if (splitSha.equals(givenSha)) {
            Main.exitWithMessage("Given branch is an ancestor of the current "
                    + "branch.");
        } else if (splitSha.equals(curSha)) {
            checkoutBranch(branchName);
            Main.exitWithMessage("Current branch fast-forwarded.");
        }

        for (String fileName: getUntrackedFiles()) {
            if (!myEquals(split.getShafromName(fileName),
                    given.getShafromName(fileName))) {
                Main.exitWithMessage("There is an untracked file in the way;"
                        + " delete it, or add and commit it first.");
            }
        }
        mergeGiven(givenSha, splitSha, curSha);
        mergeConflicts(givenSha, splitSha, curSha);
        commit("Merged " + branchName + " into " + _currBranch
                + ".", givenSha);
    }
    /** Returns the closest split point for merging a given branch, with
     *  commit GIVEN at its head and SHA-1 GIVENSHA, and the current branch
     *  with commit CUR at the head and SHA-1 CURSHA. */
    public String findSplit(String curSha, Commit cur, String givenSha,
                            Commit given) {
        HashSet<String> parents = new HashSet<>();
        parents.add(givenSha);
        addParents(given, parents);
        return findAncestor(curSha, cur, parents);
    }
    /** Adds all parents of given commit COM to the hashSet of its parents,
     *  PSET. */
    public void addParents(Commit com, HashSet<String> pSet) {
        String p1 = com.getParent1Sha();
        String p2 = com.getParent2Sha();
        if (p1 != null) {
            pSet.add(p1);
            addParents(getCommitFromSha(p1), pSet);
        }
        if (p2 != null) {
            pSet.add(p2);
            addParents(getCommitFromSha(p2), pSet);
        }
    }
    /** Returns the earliest ancestor of the given commit COM with SHA-1 COMSHA
     *  that is in the set of the other branch's ancestors, PSET. */
    public String findAncestor(String comSha, Commit com,
                               HashSet<String> pSet) {
        Queue<String> directAncestorsSha = new LinkedList<>();
        Queue<Commit> directAncestorsCom = new LinkedList<>();
        directAncestorsSha.add(comSha);
        directAncestorsCom.add(com);
        String ancest;
        Commit ancesCom;
        while (!directAncestorsSha.isEmpty()) {
            ancest = directAncestorsSha.poll();
            ancesCom = directAncestorsCom.poll();
            if (pSet.contains(ancest)) {
                return ancest;
            } else {
                if (ancesCom.getParent1Sha() != null) {
                    directAncestorsSha.offer(ancesCom.getParent1Sha());
                    directAncestorsCom.offer(getCommitFromSha(
                            ancesCom.getParent1Sha()));
                }
                if (ancesCom.getParent2Sha() != null) {
                    directAncestorsSha.offer(ancesCom.getParent2Sha());
                    directAncestorsCom.offer(getCommitFromSha(
                            ancesCom.getParent2Sha()));
                }
            }
        }
        return null;
    }
    /** Merges files changed in the given branch, with head ID GIVENID since
     *  the split point with ID SPLITID, but not in the current branch, with
     *  head ID CURID. */
    public void mergeGiven(String givenID, String splitID, String curID)
            throws IOException {
        Commit given = getCommitFromSha(givenID);
        Commit split = getCommitFromSha(splitID);
        Commit cur = getCommitFromSha(curID);
        for (String fileName: given.getShaToName().values()) {
            if (myEquals(split.getShafromName(fileName),
                    cur.getShafromName(fileName))
                    && !myEquals(split.getShafromName(fileName),
                    given.getShafromName(fileName))) {
                checkoutFile(givenID, fileName);
                add(fileName);
            }
        }
        for (String fileName: split.getShaToName().values()) {
            if (myEquals(split.getShafromName(fileName),
                    cur.getShafromName(fileName))
                    && !given.contains(fileName)) {
                rm(fileName);
            }
        }
    }
    /** Merges conflicting files between the given and current branch, with
     *  SHA-1 id's GIVENID and CURID respectively, with split point SPLITID. */
    public void mergeConflicts(String givenID, String splitID, String curID)
            throws IOException {
        Commit given = getCommitFromSha(givenID);
        Commit split = getCommitFromSha(splitID);
        Commit cur = getCommitFromSha(curID);
        File filePath;
        boolean encounteredConf = false;
        StringBuilder content = new StringBuilder();
        Set<String> mergedSet = new HashSet<>(given.getNameToSha().keySet());
        mergedSet.addAll(cur.getNameToSha().keySet());

        for (String fileName: mergedSet) {
            if (!myEquals(split.getShafromName(fileName),
                    cur.getShafromName(fileName))
                    && !myEquals(split.getShafromName(fileName),
                    given.getShafromName(fileName))
                    && !myEquals(given.getShafromName(fileName),
                    cur.getShafromName(fileName))) {
                content.append("<<<<<<< HEAD\n");
                if (cur.getShafromName(fileName) != null) {
                    content.append(cur.getFileContentsAsString(fileName));
                }
                content.append("=======\n");
                if (given.getShafromName(fileName) != null) {
                    content.append(given.getFileContentsAsString(fileName));
                }
                content.append(">>>>>>>\n");
                filePath = Utils.join(_CWD, fileName);
                filePath.createNewFile();

                Utils.writeContents(filePath, content.toString());
                content.setLength(0);
                add(fileName);
                encounteredConf = true;
            }
        }
        if (encounteredConf) {
            System.out.println("Encountered a merge conflict.");
        }
    }
    /** Remove the current branch with name BRANCHNAME, deleting its pointer,
     *  not its commits. */
    public void rmBranch(String branchName) {
        if (branchName.equals(_currBranch)) {
            Main.exitWithMessage("Cannot remove the current branch.");
        }
        File branchRef = Utils.join(_refs, branchName);
        checkBranchValid(branchName);
        branchRef.delete();
    }

    /** Checkout BRANCHNAME by putting all of the files from the given branch
     * in the working directory and overwriting existing versions if they
     * exist. */
    public void checkoutBranch(String branchName) throws IOException {
        if (branchName.equals(_currBranch)) {
            Main.exitWithMessage("No need to checkout the current branch.");
        }
        File branchRef = Utils.join(_refs, branchName);
        if (!branchRef.exists()) {
            Main.exitWithMessage("No such branch exists.");
        }
        String branchHeadSha = Utils.readContentsAsString(branchRef);
        checkoutBranchWithSha(branchHeadSha);
        updateHead(branchName);
    }
    /** Checkout the commit with id SHA by putting all of the files from the
     *  given branch in the working directory and overwriting existing
     *  versions if they exist. */
    public void checkoutBranchWithSha(String sha) throws IOException {
        Commit curBranch = getCommitFromSha(lastCommitSha());
        Commit branchHeadCom = getCommitFromSha(sha);
        if (branchHeadCom == null) {
            Main.exitWithMessage("No commit with that id exists.");
        }
        File[] currDirFiles = _CWD.listFiles();
        if (currDirFiles != null) {
            String curDirFileName, comFileSha, curDirFileSha;
            for (File file: currDirFiles) {
                curDirFileName = file.getName();
                comFileSha = branchHeadCom.getShafromName(curDirFileName);
                if (curBranch.getShafromName(curDirFileName) == null
                        && comFileSha != null) {
                    curDirFileSha = getShafromFile(file, curDirFileName);
                    if (!curDirFileSha.equals(comFileSha)) {
                        Main.exitWithMessage("There is an untracked file in"
                                + " the way; delete it, or add and commit it"
                                + " first.");
                    }
                }
            }
            for (String fileName: branchHeadCom.getShaToName().values()) {
                checkoutFile(sha, fileName);
            }
            for (File workDirFile: currDirFiles) {
                if (!branchHeadCom.contains(workDirFile.getName())) {
                    workDirFile.delete();
                }
            }
        }
    }
    /** Unstages FILENAME for addition and stages it for removal. Deletes
     * file if tracked in the current commit.*/
    public void rm(String fileName) throws IOException {
        _removeStage.stage(fileName, getCommitFromSha(lastCommitSha()),
                _addStage);
    }
    /** Starting at the current head commit, displays information about each
     *  commit backwards along the commit tree. */
    public void log() {
        String curSha = lastCommitSha();
        Commit curCommit;
        while (curSha != null) {
            curCommit = getCommitFromSha(curSha);
            curCommit.print(curSha);
            curSha = curCommit.getParent1Sha();
        }
    }
    /** Displays information about all commits every made. */
    public void globalLog() {
        Commit commit;
        for (String sha: _commitTree.keySet()) {
            commit = getCommitFromSha(sha);
            commit.print(sha);
        }
    }
    /** Print out ids of all commits with commit message MESSAGE. */
    public void find(String message) {
        boolean found = false;
        for (String sha: _commitTree.keySet()) {
            if (getCommitFromSha(sha).getMessage().equals(message)) {
                System.out.println(sha);
                found = true;
            }
        }
        if (!found) {
            Main.exitWithMessage("Found no commit with that message");
        }
    }

    /** Displays what branches currently exist, and marks the current branch
     * with a *. Also displays what files have been staged for addition or
     * removal. */
    public void status() {
        String[] branches = _branches.list();
        Arrays.sort(branches);
        statusPrint(List.of(branches), "Branches");

        List<String> addedFiles =
                new ArrayList<>(_addStage.getStagedNameToSha().keySet());
        Collections.sort(addedFiles);
        statusPrint(addedFiles, "Staged Files");

        List<String> removedFiles =
                new ArrayList<>(_removeStage.getStagedNameToSha().keySet());
        Collections.sort(addedFiles);
        statusPrint(removedFiles, "Removed Files");

        Commit lastCommit = getCommitFromSha(lastCommitSha());
        List<String> cwdFiles = new ArrayList<>(Utils.plainFilenamesIn(_CWD));
        List<String> modified = new ArrayList<>();
        String cwdFileSha;
        for (String fileName: cwdFiles) {
            cwdFileSha = getShafromFile(Utils.join(_CWD, fileName), fileName);
            if ((lastCommit.contains(fileName)
                    && !_addStage.isStaged(fileName)
                    && !_removeStage.isStaged(fileName)
                    && !lastCommit.getShafromName(fileName).equals(cwdFileSha))
                || (_addStage.isStaged(fileName)
                    && !_addStage.getShafromName(fileName)
                    .equals(cwdFileSha))) {
                modified.add(fileName + " (modified)");
            }
        }
        for (String addedFile: addedFiles) {
            if (!Utils.join(_CWD, addedFile).exists()) {
                modified.add(addedFile + " (deleted)");
            }
        }
        for (String comFile: lastCommit.getNameToSha().keySet()) {
            if (!_removeStage.isStaged(comFile)
                    && !Utils.join(_CWD, comFile).exists()) {
                modified.add(comFile + " (deleted)");
            }
        }
        statusPrint(modified, "Modifications Not Staged For Commit");

        List<String> untrackedFiles = getUntrackedFiles();
        Collections.sort(untrackedFiles);
        statusPrint(untrackedFiles, "Untracked Files");

    }
    public List<String> getUntrackedFiles() {
        List<String> untrackedFiles = new ArrayList<>();
        Commit lastCommit = getCommitFromSha(lastCommitSha());
        List<String> cwdFiles = new ArrayList<>(Utils.plainFilenamesIn(_CWD));
        for (String fileName: cwdFiles) {
            if ((!_addStage.isStaged(fileName)
                    && !lastCommit.contains(fileName))
                    ||  (_removeStage.isStaged(fileName)
                    && lastCommit.contains(fileName))) {
                untrackedFiles.add(fileName);
            }
        }
        return untrackedFiles;
    }
    /** Prints the status for the collection C with header TYPE. */
    public void statusPrint(Collection<String> c, String type) {
        System.out.println(String.format("=== %s ===", type));
        if (c != null) {
            for (String s : c) {
                if (type.equals("Branches") && s.equals(_currBranch)) {
                    System.out.println("*" + s);
                } else {
                    System.out.println(s);
                }
            }
        }
        System.out.println();
    }
    /** Adds a copy of the file FILENAME as it currently exists to the staging
     *  area. */
    public void add(String fileName) throws IOException {
        _addStage.stage(fileName, getCommitFromSha(lastCommitSha()),
                _removeStage);
    }
    /** Returns the most recent commit's sha-1 on this branch. */
    public String lastCommitSha() {
        File head = new File(Utils.readContentsAsString(_headFile));
        return Utils.readContentsAsString(head);
    }
    /** Return the Commit object labeled SHA, reading it from the current
     *  branch directory. */
    public Commit getCommitFromSha(String sha) {
        if (sha.length() < SHALENGTH) {
            Set<String> commitShas = _commitTree.keySet();
            for (String commitID: commitShas) {
                if (commitID.startsWith(sha)) {
                    sha = commitID;
                }
            }
        }
        File commitLoc = _commitTree.get(sha);
        if (commitLoc == null) {
            return null;
        }
        return Utils.readObject(commitLoc, Commit.class);
    }
    /** Return sha-1 code from the contents of the file FILEPATH with
     *  name NAME. */
    public static String getShafromFile(File filePath, String name) {
        byte[] contents = Utils.readContents(filePath);
        return Utils.sha1(contents, name);
    }

    /** Write serialized repo into _repoSavePath for persistence. */
    public void serialize() throws IOException {
        _repoSavePath.createNewFile();
        Utils.writeObject(_repoSavePath, this);
    }
    /** Delete all files in directory FOLDER. */
    public static void deleteDirFiles(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file: files) {
                file.delete();
            }
        }
    }
    /** Reads the contents of File A and writes to File B. */
    public static void writeFromFile(File a, File b) {
        Utils.writeContents(b, Utils.readContents(a));
    }
    /** Clear add and remove staging. */
    public void clearStaging() {
        _addStage.clear();
        _removeStage.clear();
    }
    /** Return directory where commit files are stored. */
    public File getCommitPath() {
        return _objects;
    }
    /** Return current branch name. */
    public String getCurBranch() {
        return _currBranch;
    }
    /** Return the repo's addStage. */
    public Staging getAddStage() {
        return _addStage;
    }
    /** Return the repo's removeStage. */
    public Staging getRemStage() {
        return _removeStage;
    }
    /** Returns if S1 and S2 are equal, overriding for null comparisons.*/
    public static boolean myEquals(String s1, String s2) {
        if (s1 == null) {
            return s2 == null;
        }
        return s1.equals(s2);
    }
    /** Compares the contents of a commit at the head of BRANCHNAME with a
     *  working directory, and presents any differences as a unified diff. */
    public void diff(String branchName) {
        checkBranchValid(branchName);
        Diff diffChecker;
        Commit com = getCommitFromSha(Utils.readContentsAsString(
                Utils.join(_refs, branchName)));
        File[] cwdFiles = _CWD.listFiles();
        File cwdFile;
        String cwdFileName;
        ArrayList<String> branchFiles =
                new ArrayList<>(com.getShaToName().values());
        Collections.sort(branchFiles);
        for (String fileName: branchFiles) {
            cwdFile = Utils.join(_CWD, fileName);

            diffChecker = new Diff();
            diffChecker.setSequences(com.getFileFromSha(com.
                    getShafromName(fileName)), cwdFile);
            if (diffChecker.diffs().length != 0) {
                cwdFileName = "b/" + fileName;
                if (!cwdFile.exists()) {
                    cwdFileName = "/dev/null";
                }
                System.out.printf("diff --git a/%s %s%n", fileName,
                        cwdFileName);
                System.out.printf("--- a/%s%n", fileName);
                System.out.printf("+++ %s%n", cwdFileName);
                parseDiff(diffChecker);
            }
        }
    }
    /** Compares the contents of a commit at the head of BRANCH1 with the
     * head of BRANCH2, and presents any differences as a unified diff. */
    public void diff(String branch1, String branch2) {
        checkBranchValid(branch1);
        checkBranchValid(branch2);

        Diff diffChecker;
        Commit com1 = getCommitFromSha(Utils.readContentsAsString(
                Utils.join(_refs, branch1)));
        Commit com2 = getCommitFromSha(Utils.readContentsAsString(
                Utils.join(_refs, branch2)));

        Set<String> branchFilesSet =
                new HashSet<>(com1.getNameToSha().keySet());
        branchFilesSet.addAll(com2.getNameToSha().keySet());
        ArrayList<String> branchFiles = new ArrayList<>(branchFilesSet);
        Collections.sort(branchFiles);

        String b1FileName, b2FileName;
        for (String fileName: branchFiles) {
            b1FileName = "a/" + fileName;
            b2FileName = "b/" + fileName;
            if (!com1.contains(fileName)) {
                b1FileName = "/dev/null";
            }
            if (!com2.contains(fileName)) {
                b2FileName = "/dev/null";
            }

            diffChecker = new Diff();
            diffChecker.setSequences(com1.getFileFromSha(com1.
                    getShafromName(fileName)), com2.getFileFromSha(com2.
                    getShafromName(fileName)));

            if (diffChecker.diffs().length != 0) {
                System.out.printf("diff --git %s %s%n", b1FileName,
                        b2FileName);
                System.out.printf("--- %s%n", b1FileName);
                System.out.printf("+++ %s%n", b2FileName);
                parseDiff(diffChecker);
            }
        }
    }
    /** Exits with error if BRANCH is not found. */
    private void checkBranchValid(String branch) {
        if (!Utils.join(_refs, branch).exists()) {
            Main.exitWithMessage("A branch with that name does not exist.");
        }
    }

    /* Parses the result of the Diff utility to create the proper output using
     the given DIFFCHECKER. */
    public void parseDiff(Diff diffChecker) {
        int[] result = diffChecker.diffs();
        int L1, N1, L2, N2;
        StringBuilder output = new StringBuilder();
        StringBuilder lines = new StringBuilder();
        String header;
        String n1Fix, n2Fix;
        for (int i = 0; i < result.length; i += 4) {
            L1 = result[i];
            N1 = result[i + 1];
            L2 = result[i + 2];
            N2 = result[i + 3];

            for (int j = L1; j <= L1 + N1 - 1; j++) {
                lines.append("-").append(diffChecker.get1(j)).append("\n");
            }
            for (int j = L2; j <= L2 + N2 - 1; j++) {
                lines.append("+").append(diffChecker.get2(j)).append("\n");
            }
            if (N1 != 0) {
                L1 += 1;
            }
            if (N2 != 0) {
                L2 += 1;
            }
            n1Fix = "," + N1;
            n2Fix = "," + N2;
            if (N1 == 1) {
                n1Fix = "";
            }
            if (N2 == 1) {
                n2Fix = "";
            }
            header = String.format("@@ -%s%s +%s%s @@%n", L1, n1Fix, L2, n2Fix);
            output.append(header).append(lines);
            lines.setLength(0);
        }
        System.out.print(output);
    }
}
