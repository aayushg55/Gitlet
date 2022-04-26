package gitlet;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;

public class Staging implements Serializable {
    /** Directory for staging files. */
    private File _stagePath;
    /** Directory where repository is created. */
    private File _CWD;
    /** HashMap of file name to Sha-1 code. */
    private HashMap<String, String> _nameToSha = new HashMap<>();
    /** HashMap of file name to staged file path. */
    private HashMap<String, File> _nameToPath = new HashMap<>();
    /** HashMap of Sha-1 code to staged file name. */
    private HashMap<String, String> _shaToName = new HashMap<>();
    /** HashMap of Sha-1 code to file path. */
    private HashMap<String, File> _shaToFile = new HashMap<>();
    /** Type of staging area: add or remove. */
    private String _type;
    /** Type of opposite staging area: add or remove. */
    private String _oType;

    public Staging(String type, File dir, File cwd) {
        _type = type;
        if (_type.equals("add")) {
            _oType = "remove";
        } else {
            _oType = "add";
        }
        _stagePath = Utils.join(dir, type);
        _CWD = cwd;
        _stagePath.mkdir();
    }

    /** Stage FILENAME to this staging area for RECCOMMIT with the staging
     *  area of the opposite type OTHER.*/
    public void stage(String fileName, Commit recCommit, Staging other)
            throws IOException {
        File filePath = Utils.join(_CWD, fileName);
        String fileSha = null;
        if (_type.equals("add")) {
            if (!filePath.exists()) {
                Main.exitWithMessage("File does not exist.");
            }
            fileSha = Repository.getShafromFile(filePath, fileName);
            other.unstage(fileName, fileSha);
            if (recCommit.contains(fileSha)) {
                unstage(fileName, fileSha);
            } else {
                copyFile(fileSha, filePath, fileName);
            }
        } else {
            if (!recCommit.contains(fileName)
                    && !other.getStagedNameToPath().containsKey(fileName)) {
                Main.exitWithMessage("No reason to remove the file.");
            } else {
                fileSha = other.getShafromName(fileName);
                if (fileSha == null) {
                    fileSha = recCommit.getShafromName(fileName);
                }
                if (recCommit.contains(fileName)) {
                    addToMap(fileName, fileSha, Utils.join(_stagePath,
                            fileSha));
                    Utils.restrictedDelete(filePath);
                }
                other.unstage(fileName, fileSha);
            }
        }

    }
    /** Clear staging area. */
    public void clear() {
        Repository.deleteDirFiles(_stagePath);
        _nameToSha.clear();
        _shaToFile.clear();
        _shaToName.clear();
        _nameToPath.clear();
    }

    /** Unstage FILENAME stored in STAGEDFILEPATH with Sha-1 id SHA. */
    public void unstage(String fileName, String sha) {
        removeFromMap(fileName, sha);
        Utils.join(_stagePath, sha).delete();
    }
    /** Copies FILENAME with Sha-1 SHA from FILEPATH to staging area, replacing
     * contents if it exists. */
    public void copyFile(String sha, File filePath, String fileName)
            throws IOException {
        File stagedFilePath = Utils.join(_stagePath, sha);
        removeFromMap(fileName, sha);
        if (!stagedFilePath.exists()) {
            stagedFilePath.createNewFile();
        }
        addToMap(fileName, sha, stagedFilePath);
        Repository.writeFromFile(filePath, stagedFilePath);
    }
    /** Remove FILENAME with SHA-1 SHA from staging HashMaps. */
    public void removeFromMap(String fileName, String sha) {
        String oldSha = _nameToSha.remove(fileName);
        _shaToFile.remove(oldSha);
        _shaToName.remove(oldSha);
        _nameToPath.remove(fileName);
    }
    /** Add FILENAME with SHA-1 SHA and file path PATH to Staging HashMaps. */
    public void addToMap(String fileName, String sha, File path) {
        _nameToSha.put(fileName, sha);
        _shaToFile.put(sha, path);
        _shaToName.put(sha, fileName);
        _nameToPath.put(fileName, path);
    }
    /** Return Sha-1 code from FILENAME. */
    public String getShafromName(String fileName) {
        return _nameToSha.get(fileName);
    }
    /** Returns the hashmap of file names to their file paths. */
    public HashMap<String, File> getStagedNameToPath() {
        return _nameToPath;
    }
    /** Returns the hashmap of file names to their Sha id. */
    public HashMap<String, String> getStagedNameToSha() {
        return _nameToSha;
    }
    /** Returns whether FILENAME is staged. */
    public boolean isStaged(String fileName) {
        return getShafromName(fileName) != null;
    }
}
