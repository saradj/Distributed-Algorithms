package cs451;

import java.io.*;
import java.util.List;
import java.util.stream.Collectors;

public class ConfigParser {

    private String path;
    private int numberOfMessages;
    private List<String> dependencies;


    public boolean populate(String value) {
        File file = new File(value);
        path = file.getPath();
        try (BufferedReader br = new BufferedReader(new FileReader(value))) {
            List<String> lines = br.lines().collect(Collectors.toList());
            if (lines.size() < 1)
                throw new IllegalArgumentException("You should have at least one line in the config file");
            this.numberOfMessages = Integer.parseInt(lines.get(0).trim());
            this.dependencies = lines;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return true;
    }

    public String getPath() {
        return path;
    }

    public int getNumberOfMessages() {
        return numberOfMessages;
    }

    public List<String> getDependencies() {
        return dependencies;
    }
}
