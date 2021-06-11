import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

public class Main {
    private static final boolean rapid = true;
    private static final boolean debug = false;

    public static void main(String[] args) throws Exception{
        List<String> files;
        File folder;
        System.out.println("Generating intermediate code... \nMiniJava -> LLVM");
        //read arguments
        if(args.length<=0) {
            args = new String[1];
            args[0] = "./";
        }
        for(int i=0; i<args.length;i++){
            folder = new File(args[i]);
            //if argument is a directory check all java files it contains
            if(folder.isDirectory() &&
               !folder.getName().contains("error") &&
               !folder.getName().contains("ERROR"))
            {
                files = readDir(args[i]);
                for (int j=0; j<files.size();j++)
                    generateFile(files.get(j));
            }
            //if argument is file check it
            else if(folder.isFile())
                generateFile(args[i]);
        }
    }

    //pass the file to the ir generator
    public static void generateFile(String filename) throws Exception{
        PrintStream original = System.out;
        if(filename.contains("ERROR") || filename.contains("error"))
            return;
        String[] simplename = filename.split("/");
        if (!rapid) {//non-rapid execution
            System.out.println("\u001B[33mPress Enter to compile \"" + simplename[simplename.length-1] + "\" file...\u001B[0m");
            (new Scanner(System.in)).nextLine();        //it exists to give time to the programmer to check if the output is correct
            System.out.print("\u001B[F\r");
        }
        if (!debug) {//non-debug, dont show debug messages
            System.setOut(new PrintStream(new OutputStream() {public void write(int b) {}}));
        }
        try{
            Compiler.generate(filename);
        } catch (Exception e) {
            System.setOut(original);
            System.out.println(ANSI_RED+"Encountered ERROR in file " + filename+ANSI_RESET);
            System.out.println(ANSI_RED+e.getMessage()+ANSI_RESET);
            e.printStackTrace();
            return;
        }
        if (!debug) {//non-debug, show success message
            System.setOut(original);
            String[] temp = filename.split("/");
            filename = filename.substring(0, filename.length() - ".java".length());
            System.out.println(ANSI_GREEN+"File " + filename  + ".ll successfully created."+ANSI_RESET);
        }
//        printList(readAllLines(filename.substring(0, filename.length() - 5) + ".out"));
    }

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_RED = "\u001B[31m";

    //read directory and store all java files to check them later
    public static List<String> readDir(String arg){
        List<String> files = new ArrayList<>();
        File folder = new File(arg);
        File[] listOfFiles = folder.listFiles();
        for (File file : listOfFiles) {
            if (file.isFile()) {
                if(file.getName().endsWith(".java")){
                    files.add(file.getPath());
                }
            }
            else if(file.isDirectory() && !file.getName().contains("error") && !folder.getName().contains("ERROR")) {
                files.addAll(readDir(file.getPath()));
            }
        }
        return files;
    }

    //read a file
    public static List<String> readAllLines(String fileName) {
        List<String> result = new ArrayList<>();
        try {
            result.addAll(Files.readAllLines(Paths.get(fileName)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    //print a list (used to print a read file)
    public static void printList(List<String> list){
        if(list==null)
            return;
        for(int i=0; i<list.size(); i++)
            System.out.println(list.get(i));
    }
}
