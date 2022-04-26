package gitlet;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

public class Commit implements Serializable {

    /** Date for this commit. */
    private final String _date;
    /** Name of branch commit is on. */
    private final String _branch;
    /** Commit message. */
    private final String _message;
    /** Blob storage directory. */
    private final File _objects;
    /** Commit parent1. */
    private transient Commit _parent1;
    /** Commit parent1 sha-1 String. */
    private String _parent1Sha;
    /** Commit parent2. */
    private transient Commit _parent2;
    /** Commit parent2 sha-1 String. */
    private String _parent2Sha;
    /** HashMap of file name to sha-1 code. */
    private HashMap<String, String> _nameToSha = new HashMap<>();
    /** HashMap of sha-1 code to file name. */
    private HashMap<String, String> _shaToName = new HashMap<>();
    /** HashMap of sha-1 code to file path. */
    private HashMap<String, File> _shaToFile = new HashMap<>();
    public Commit(String message, String p1, Commit c1, String p2, Commit c2,
                  Repository repo) throws IOException {
        _objects = repo.getCommitPath();
        _branch = repo.getCurBranch();
        this._message = message;
        if (message == null || message.equals("")) {
            Main.exitWithMessage("Please enter a commit message.");
        }
        _parent1Sha = p1;
        _parent1 = c1;
        _parent2Sha = p2;
        _parent2 = c2;

        if (_parent1Sha == null) {
            _date = time(0);
        } else {
            _date = time(1);
        }
        if (_parent1 != null) {
            _nameToSha = _parent1._nameToSha;
            _shaToName = _parent1._shaToName;
            _shaToFile = _parent1._shaToFile;
            checkStaging(repo.getAddStage(), repo.getRemStage());
        }
    }
    /** Makes changes to the commit based on files in the ADDSTAGE and
     *  REMOVESTAGE. */
    public void checkStaging(Staging addStage, Staging removeStage)
            throws IOException {
        HashMap<String, File> addStagedFilePathMap =
                addStage.getStagedNameToPath();
        HashMap<String, File> remStagedFilePathMap =
                removeStage.getStagedNameToPath();
        if (addStagedFilePathMap.keySet().size() == 0
                && remStagedFilePathMap.keySet().size() == 0) {
            Main.exitWithMessage("No changes added to the commit.");
        }
        for (String fileName: addStagedFilePathMap.keySet()) {
            copyFile(addStage.getShafromName(fileName),
                    addStagedFilePathMap.get(fileName), fileName);
        }

        for (String fileName: remStagedFilePathMap.keySet()) {
            removeFromMap(fileName);
        }
        addStage.clear();
        removeStage.clear();
    }

    /** Copies FILENAME with sha-1 SHA from FILEPATH to staging area, replacing
     * contents if it exists. */
    public void copyFile(String sha, File filePath, String fileName)
            throws IOException {
        File blobPath = Utils.join(_objects, sha);
        removeFromMap(fileName);
        if (!blobPath.exists()) {
            blobPath.createNewFile();
        }
        addToMap(fileName, sha, blobPath);
        Repository.writeFromFile(filePath, blobPath);
    }
    /** Remove FILENAME from Commit HashMaps. */
    public void removeFromMap(String fileName) {
        String oldSha = _nameToSha.remove(fileName);
        _shaToFile.remove(oldSha);
        _shaToName.remove(oldSha);
    }
    /** Add FILENAME with SHA-1 SHA and path PATH to Commit HashMaps. */
    public void addToMap(String fileName, String sha, File path) {
        _nameToSha.put(fileName, sha);
        _shaToName.put(sha, fileName);
        _shaToFile.put(sha, path);
    }

    /** Returns the UNIX time formatted. Gives time 0 for T = 0
     * and otherwise returns system time. */
    private static String time(int t) {
        SimpleDateFormat formatter =
                new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z");
        if (t == 0) {
            return formatter.format(new Date(0));
        }
        return formatter.format(new Date(System.currentTimeMillis()));
    }

    /** Return the commit's commit message. */
    public String getMessage() {
        return _message;
    }
    /** Return the commit's commit message. */
    public String getDate() {
        return _date;
    }
    /** Return the commit's parent sha. */
    public String getParent1Sha() {
        return _parent1Sha;
    }
    /** Return the commit's 2nd parent sha. */
    public String getParent2Sha() {
        return _parent2Sha;
    }
    /** Return branch name of commit. */
    public String getBranch() {
        return _branch;
    }
    /** Return sha-1 code from FILENAME. */
    public String getShafromName(String filename) {
        return _nameToSha.get(filename);
    }
    /** Return filename from sha-1 code SHA. */
    public String getNameFromSha(String sha) {
        return _shaToName.get(sha);
    }
    /** Return file path from sha-1 code SHA. */
    public File getFileFromSha(String sha) {
        return _shaToFile.get(sha);
    }
    /** Returns the hashmap of sha ids to their file paths. */
    public HashMap<String, String> getShaToName() {
        return _shaToName;
    }
    /** Returns the hashmap of file names to their sha id. */
    public HashMap<String, String> getNameToSha() {
        return _nameToSha;
    }
    /** Return if Commit includes S, where S could be either a filename or
     * sha-1 ID. */
    public boolean contains(String s) {
        return _nameToSha.containsKey(s) || _shaToName.containsKey(s);
    }
    /** Print out the commit timestamp with sha CURSHA, message, sha id. */
    public void print(String curSha) {
        System.out.println("===");
        System.out.println("commit " + curSha);
        if (_parent2Sha != null) {
            System.out.println("Merge: " + _parent1Sha.substring(0, 7)
                    + " " +  _parent2Sha.substring(0, 7));
        }
        System.out.println("Date: " + _date);
        System.out.println(_message + '\n');
    }
    /** Returns contents from the FILENAME in this commit. */
    public String getFileContentsAsString(String fileName) {
        return Utils.readContentsAsString(_shaToFile.get
            (_nameToSha.get(fileName)));
    }
}
