import syntaxtree.*;
import visitor.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

public class Compiler {
    public static void generate(String arg) throws Exception {
        if(arg==null){
            System.err.println("Usage: java Main <inputFile>");
            System.exit(1);
        }
        FileInputStream fis = null;
        try{
            String filepath = arg;
            if (filepath.endsWith(".java")) {
                filepath = filepath.substring(0, filepath.length() - ".java".length());
            }
            else {
                System.err.println("Usage: java Main <inputFile>");
                System.exit(1);
            }
            String[] temp = filepath.split("/");
//            String filename = "./ll/" + temp[temp.length-1];
            String filename = filepath;

            fis = new FileInputStream(arg);
            MiniJavaParser parser = new MiniJavaParser(fis);
            Goal root = parser.Goal();

            DataVisitor eval = new DataVisitor();
            DataVisitor.symbolTable = new TreeMap<>();
            DataVisitor.methodVariables = new TreeMap<>();
            DataVisitor.classes = new ArrayList<>();
            DataVisitor.simpleClasses = new ArrayList<>();
            DataVisitor.used = new TreeMap<>();
            DataVisitor.parameters = new TreeMap<>();
            DataVisitor.symbolTables = new ArrayList<>();
            DataVisitor.useds = new ArrayList<>();
            DataVisitor.methods = new ArrayList<>();
            root.accept(eval, null);
            Helper.doubleDeclarationCheck();

            DataVisitor2 eval1 = new DataVisitor2();
            root.accept(eval1, null);
            Helper.methodsCheck();
            Helper.methodOffsets = new TreeMap<>();
            Helper.fieldOffsets = new TreeMap<>();
            Helper.vtabletype = new TreeMap<>();
            Helper.vtablemethodtype = new TreeMap<>();
            Helper.printOutput();

            for(int i=0;i<DataVisitor.classes.size();i++)
                Helper.isField(DataVisitor.classes.get(i),null);
            try{
                File ll = new File(filename + ".ll");
                ll.createNewFile();
            } catch (IOException e) {
                System.out.println("Cannot create .ll file");
                e.printStackTrace();
                throw new IOException("ERROR");
            }

            try {
                FileWriter fw = new FileWriter(filename + ".ll", false);
                for(int i=0;i<DataVisitor.simpleClasses.size();i++)
                    fw.write(Helper.VTable(DataVisitor.simpleClasses.get(i)));
                fw.write(Helper.VTable(DataVisitor.mainClass));
                fw.write(Helper.LLHeader());
                fw.close();
            } catch (IOException ioe) {
                System.err.println("FileWriter error: " + ioe.getMessage());
                throw new IOException("ERROR");
            }

            Generator ll = new Generator();
            Generator.counter = new TreeMap<>();
            Generator.varRegs = new TreeMap<String, Map<String, String>>();
            Generator.labcounter = 0;
            Generator.labels = new TreeMap<String, Map<String, String>>();
            root.accept(ll, null);
            try {
                FileWriter fw = new FileWriter(filename + ".ll", true);
                fw.write(Generator.emit);
                fw.close();
            } catch (IOException ioe) {
                System.err.println("FileWriter error: " + ioe.getMessage());
                throw new IOException("ERROR");
            }

            System.out.println(ANSI_GREEN+"File " + filename.substring(1)  + ".ll successfully created."+ANSI_RESET);
            System.out.println(Helper.methodOffsets);
            DataVisitor.symbolTable = null;
            DataVisitor.classes = null;
            DataVisitor.simpleClasses = null;
            DataVisitor.used = null;
            DataVisitor.parameters = null;
            DataVisitor.symbolTables = null;
            DataVisitor.useds = null;
            DataVisitor.methods = null;
            System.gc();
        }
        catch(ParseException ex){
            System.out.println(ANSI_RED+"Caught "+ex.getMessage()+" in file "+arg+ANSI_RESET);
            ex.printStackTrace();
            DataVisitor.symbolTable = null;
            DataVisitor.classes = null;
            DataVisitor.simpleClasses = null;
            DataVisitor.used = null;
            DataVisitor.parameters = null;
            DataVisitor.symbolTables = null;
            DataVisitor.useds = null;
            DataVisitor.methods = null;
            System.gc();
            throw new ParseException("ERROR");
        }
        catch(FileNotFoundException ex){
            System.err.println(ANSI_BLUE+ex.getMessage()+ANSI_RESET);
        }
        finally{
            try{
                if(fis != null) fis.close();
            }
            catch(IOException ex){
                System.err.println(ex.getMessage());
                throw new IOException("ERROR");
            }
        }
    }

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_BLUE = "\u001B[34m";
}


class DataVisitor extends GJDepthFirst<String, String>{

    public static Map<String, Map<Integer,String>> symbolTable;    //scope integer, string variable, memory position integer
    public static ArrayList<String> classes;
    public static ArrayList<String> simpleClasses;
    public static String mainClass;
    public static Map<String, Map<Integer,String>> used;
    public static Map<String, Map<Integer,String>> methodVariables;
    public static Map<String, Map<Integer,String>> parameters;
    public static ArrayList<String> symbolTables;
    public static ArrayList<String> useds;
    public static ArrayList<String> methods;

    /**
     * f0 -> MainClass()
     * f1 -> ( TypeDeclaration() )*
     * f2 -> <EOF>
     */
    public String visit(Goal n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> "public"
     * f4 -> "static"
     * f5 -> "void"
     * f6 -> "main"
     * f7 -> "("
     * f8 -> "String"
     * f9 -> "["
     * f10 -> "]"
     * f11 -> Identifier()
     * f12 -> ")"
     * f13 -> "{"
     * f14 -> ( VarDeclaration() )*
     * f15 -> ( Statement() )*
     * f16 -> "}"
     * f17 -> "}"
     */
    public String visit(MainClass n, String argu) throws Exception {
//        n.f0.accept(this, argu);
        String classname = n.f1.accept(this, argu);
//        System.out.println("Class: " + classname);
        Map<Integer,String> scope = new TreeMap<>();
        symbolTable.put(classname, scope);
        symbolTables.add(classname);
        classes.add(classname);
        mainClass = classname;
        Map<Integer,String> scope1 = new TreeMap<>();
        methodVariables.put(classname+"."+"main" , scope1);
//        n.f2.accept(this, argu);
//        n.f3.accept(this, argu);
//        n.f4.accept(this, argu);
//        n.f5.accept(this, argu);
//        n.f6.accept(this, argu);
//        n.f7.accept(this, argu);
//        n.f8.accept(this, argu);
//        n.f9.accept(this, argu);
//        n.f10.accept(this, argu);
//        n.f11.accept(this, argu);
//        n.f12.accept(this, argu);
//        n.f13.accept(this, argu);
        n.f14.accept(this, classname+"."+"main");
//        n.f15.accept(this, argu);
//        n.f16.accept(this, argu);
//        n.f17.accept(this, argu);
//        scope.put("myVar",12);
//        super.visit(n, classname);
//        System.out.println();
        return null;
    }

    /**
     * f0 -> ClassDeclaration()
     *       | ClassExtendsDeclaration()
     */
    public String visit(TypeDeclaration n, String argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> ( VarDeclaration() )*
     * f4 -> ( MethodDeclaration() )*
     * f5 -> "}"
     */
    public String visit(ClassDeclaration n, String argu) throws Exception {
        n.f0.accept(this, argu);
        String classname = n.f1.accept(this, argu);
//        System.out.println("Class: " + classname );
        Map<Integer,String> scope = new TreeMap<>();
        symbolTable.put(classname , scope);
//        n.f2.accept(this, argu);
        n.f3.accept(this, classname);
        n.f4.accept(this, classname);
//        n.f5.accept(this, argu);
        symbolTables.add(classname);
        classes.add(classname);
        simpleClasses.add(classname);
//        super.visit(n, classname);
//        System.out.println();
        return null;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "extends"
     * f3 -> Identifier()
     * f4 -> "{"
     * f5 -> ( VarDeclaration() )*
     * f6 -> ( MethodDeclaration() )*
     * f7 -> "}"
     */
    public String visit(ClassExtendsDeclaration n, String argu) throws Exception {
        n.f0.accept(this, argu);
        String classname = n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        String extended = n.f3.accept(this, argu);
        String extendOf = " extends " + extended;
//        if(!simpleClasses.contains(extended)){
//            System.out.println("ERROR: undefined parent class: " + extended);
//            throw new ParseException("ERROR");
//        }
//        System.out.println("Class: " + classname+extendOf );
        Map<Integer,String> scope = new TreeMap<>();
        symbolTable.put(classname+extendOf , scope);
//        n.f4.accept(this, argu);
        n.f5.accept(this, classname+extendOf);
        n.f6.accept(this, classname+extendOf);
//        n.f7.accept(this, argu);
        classes.add(classname+extendOf);
        simpleClasses.add(classname);
        symbolTables.add(classname+extendOf);
//        super.visit(n, classname+extendOf);
//        System.out.println();
        return null;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     * f2 -> ";"
     */
    public String visit(VarDeclaration n, String argu) throws Exception{
        String type = n.f0.accept(this, argu);
        String name = n.f1.accept(this, argu);
        n.f2.accept(this, argu);
//        System.out.println(type + " " + name);
        String[] temp = argu.split("[.]");
        if(temp.length>1 && temp[1].equals("main"))
            symbolTable.get(temp[0]).put(symbolTable.get(temp[0]).size(),type + " " + name);
        else
            symbolTable.get(argu).put(symbolTable.get(argu).size(),type + " " + name);
        if(methodVariables.containsKey(argu))
            methodVariables.get(argu).put(methodVariables.get(argu).size() , type + " " + name);
        return null;
    }

    /**
     * f0 -> "public"
     * f1 -> Type()
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( FormalParameterList() )?
     * f5 -> ")"
     * f6 -> "{"
     * f7 -> ( VarDeclaration() )*
     * f8 -> ( Statement() )*
     * f9 -> "return"
     * f10 -> Expression()
     * f11 -> ";"
     * f12 -> "}"
    **/
    public String visit(MethodDeclaration n, String argu) throws Exception {
        n.f0.accept(this, argu);
        String myType = n.f1.accept(this, argu);
        String myName = n.f2.accept(this, argu);
        symbolTable.get(argu).put( symbolTable.get(argu).size(),"method " + myType  + " " + myName);
        Map<Integer,String> scope = new TreeMap<>();
        symbolTable.put(argu + "." + myName , scope);
        symbolTables.add(argu + "." + myName);
        Map<Integer,String> scope1 = new TreeMap<>();
        methodVariables.put(argu + "." + myName , scope1);
//        n.f3.accept(this, argu);
        String argumentList = n.f4.present() ? n.f4.accept(this, argu + "." + myName) : "";
        Map<Integer,String> param = new TreeMap<>();
        String[] arg = argumentList.split(",");
        if(!argumentList.equals(""))
            for(int i=0; i<arg.length;i++) {
                param.put(param.size(), arg[i].trim());
                scope1.put(scope1.size(), arg[i].trim());
            }
        parameters.put(argu + "." + myName , param);
//        n.f5.accept(this, argu);
//        n.f6.accept(this, argu);
        n.f7.accept(this, argu + "." + myName);
//        n.f8.accept(this, argu);
//        n.f9.accept(this, argu);
        n.f10.accept(this, argu + "." + myName);
        String retval = n.f10.accept(this, argu + "." + myName);
//        DataVisitor.methods.add(argu + "." + myName + "/" + myType + "/" + retval);//        n.f11.accept(this, argu);
//        n.f11.accept(this, argu);
//        n.f12.accept(this, argu);


        return null;
    }

    /**
     * f0 -> FormalParameter()
     * f1 -> FormalParameterTail()
     **/
    @Override
    public String visit(FormalParameterList n, String argu) throws Exception {
        String ret = n.f0.accept(this, argu);
        if (n.f1 != null) {
            ret += n.f1.accept(this, argu);
        }
        return ret;
    }

    /**
     * f0 -> FormalParameter()
     * f1 -> FormalParameterTail()
     **/
    public String visit(FormalParameterTerm n, String argu) throws Exception {
        return n.f1.accept(this, argu);
    }

    /**
     * f0 -> ","
     * f1 -> FormalParameter()
     **/
    @Override
    public String visit(FormalParameterTail n, String argu) throws Exception {
        String ret = "";
        for ( Node node: n.f0.nodes) {
            ret += ", " + node.accept(this, argu);
        }

        return ret;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     **/
    @Override
    public String visit(FormalParameter n, String argu) throws Exception{
        String type = n.f0.accept(this, argu);
        String name = n.f1.accept(this, argu);
        symbolTable.get(argu).put(symbolTable.get(argu).size(),type + " " + name);
        return  type + " " + name;
    }
    /**
     * f0 -> ArrayType()
     *       | BooleanType()
     *       | IntegerType()
     *       | Identifier()
     */
    public String visit(Type n, String argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    /**
     * f0 -> "int"
     * f1 -> "["
     * f2 -> "]"
     */
    public String visit(ArrayType n, String argu) {
        return "int[]";
    }

    /**
     * f0 -> "boolean"
     */
    public String visit(BooleanType n, String argu) {
        return "boolean";
    }


    /**
     * f0 -> "int"
     */
    public String visit(IntegerType n, String argu) {
        return "int";
    }

    /**
     * f0 -> Block()
     *       | AssignmentStatement()
     *       | ArrayAssignmentStatement()
     *       | IfStatement()
     *       | WhileStatement()
     *       | PrintStatement()
     */
    public String visit(Statement n, String argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    /**
     * f0 -> "{"
     * f1 -> ( Statement() )*
     * f2 -> "}"
     */
    public String visit(Block n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> Identifier()
     * f1 -> "="
     * f2 -> Expression()
     * f3 -> ";"
     */
    public String visit(AssignmentStatement n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> Identifier()
     * f1 -> "["
     * f2 -> Expression()
     * f3 -> "]"
     * f4 -> "="
     * f5 -> Expression()
     * f6 -> ";"
     */
    public String visit(ArrayAssignmentStatement n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        n.f5.accept(this, argu);
        n.f6.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> "if"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     * f5 -> "else"
     * f6 -> Statement()
     */
    public String visit(IfStatement n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        n.f5.accept(this, argu);
        n.f6.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> "while"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     */
    public String visit(WhileStatement n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> "System.out.println"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> ";"
     */
    public String visit(PrintStatement n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> AndExpression()
     *       | CompareExpression()
     *       | PlusExpression()
     *       | MinusExpression()
     *       | TimesExpression()
     *       | ArrayLookup()
     *       | ArrayLength()
     *       | MessageSend()
     *       | PrimaryExpression()
     */
    public String visit(Expression n, String argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "&&"
     * f2 -> PrimaryExpression()
     */
    public String visit(AndExpression n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "<"
     * f2 -> PrimaryExpression()
     */
    public String visit(CompareExpression n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "+"
     * f2 -> PrimaryExpression()
     */
    public String visit(PlusExpression n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "-"
     * f2 -> PrimaryExpression()
     */
    public String visit(MinusExpression n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "*"
     * f2 -> PrimaryExpression()
     */
    public String visit(TimesExpression n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "["
     * f2 -> PrimaryExpression()
     * f3 -> "]"
     */
    public String visit(ArrayLookup n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> "length"
     */
    public String visit(ArrayLength n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( ExpressionList() )?
     * f5 -> ")"
     */
    public String visit(MessageSend n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        n.f5.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> Expression()
     * f1 -> ExpressionTail()
     */
    public String visit(ExpressionList n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> ( ExpressionTerm() )*
     */
    public String visit(ExpressionTail n, String argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    /**
     * f0 -> ","
     * f1 -> Expression()
     */
    public String visit(ExpressionTerm n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> IntegerLiteral()
     *       | TrueLiteral()
     *       | FalseLiteral()
     *       | Identifier()
     *       | ThisExpression()
     *       | ArrayAllocationExpression()
     *       | AllocationExpression()
     *       | NotExpression()
     *       | BracketExpression()
     */
    public String visit(PrimaryExpression n, String argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    /**
     * f0 -> <INTEGER_LITERAL>
     */
    public String visit(IntegerLiteral n, String argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    /**
     * f0 -> "true"
     */
    public String visit(TrueLiteral n, String argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    /**
     * f0 -> "false"
     */
    public String visit(FalseLiteral n, String argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    /**
     * f0 -> <IDENTIFIER>
     */
    public String visit(Identifier n, String argu) throws Exception {
        String retStr = n.f0.toString();
        return retStr;
    }

    /**
     * f0 -> "this"
     */
    public String visit(ThisExpression n, String argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    /**
     * f0 -> "new"
     * f1 -> "int"
     * f2 -> "["
     * f3 -> Expression()
     * f4 -> "]"
     */
    public String visit(ArrayAllocationExpression n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> "new"
     * f1 -> Identifier()
     * f2 -> "("
     * f3 -> ")"
     */
    public String visit(AllocationExpression n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> "!"
     * f1 -> PrimaryExpression()
     */
    public String visit(NotExpression n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> "("
     * f1 -> Expression()
     * f2 -> ")"
     */
    public String visit(BracketExpression n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        return retStr;
    }
}

class DataVisitor2 extends GJDepthFirst<String, String>{

    /**
     * f0 -> MainClass()
     * f1 -> ( TypeDeclaration() )*
     * f2 -> <EOF>
     */
    public String visit(Goal n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> "public"
     * f4 -> "static"
     * f5 -> "void"
     * f6 -> "main"
     * f7 -> "("
     * f8 -> "String"
     * f9 -> "["
     * f10 -> "]"
     * f11 -> Identifier()
     * f12 -> ")"
     * f13 -> "{"
     * f14 -> ( VarDeclaration() )*
     * f15 -> ( Statement() )*
     * f16 -> "}"
     * f17 -> "}"
     */
    public String visit(MainClass n, String argu) throws Exception {
        String retStr=null;
        //        n.f0.accept(this, argu);
        String classname = n.f1.accept(this, argu);
//        n.f2.accept(this, argu);
//        n.f3.accept(this, argu);
//        n.f4.accept(this, argu);
//        n.f5.accept(this, argu);
//        n.f6.accept(this, argu);
//        n.f7.accept(this, argu);
//        n.f8.accept(this, argu);
//        n.f9.accept(this, argu);
//        n.f10.accept(this, argu);
//        n.f11.accept(this, argu);
//        n.f12.accept(this, argu);
//        n.f13.accept(this, argu);
//        n.f14.accept(this, argu);
        n.f15.accept(this, classname);
//        n.f16.accept(this, argu);
//        n.f17.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> ClassDeclaration()
     *       | ClassExtendsDeclaration()
     */
    public String visit(TypeDeclaration n, String argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> ( VarDeclaration() )*
     * f4 -> ( MethodDeclaration() )*
     * f5 -> "}"
     */
    public String visit(ClassDeclaration n, String argu) throws Exception {
        String retStr=null;
//        n.f0.accept(this, argu);
        String classname = n.f1.accept(this, argu);
//        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        n.f4.accept(this, classname);
//        n.f5.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "extends"
     * f3 -> Identifier()
     * f4 -> "{"
     * f5 -> ( VarDeclaration() )*
     * f6 -> ( MethodDeclaration() )*
     * f7 -> "}"
     */
    public String visit(ClassExtendsDeclaration n, String argu) throws Exception {
        String retStr=null;
//        n.f0.accept(this, argu);
//        n.f1.accept(this, argu);
        String classname = n.f1.accept(this, argu);
//        n.f2.accept(this, argu);
        String extendOf = " extends " + n.f3.accept(this, argu);
//        n.f4.accept(this, argu);
        n.f5.accept(this, argu);
        n.f6.accept(this, classname+extendOf);
//        n.f7.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     * f2 -> ";"
     */
    public String visit(VarDeclaration n, String argu) throws Exception{
        String type = n.f0.accept(this, argu);
//        if(!DataVisitor.simpleClasses.contains(type) && !type.equals("int") && !type.equals("int[]") && !type.equals("boolean")){
//            System.out.println("ERROR: undefined identifier: " + type);
//            throw new ParseException("ERROR");
//        }
        String name = n.f1.accept(this, argu);
        n.f2.accept(this, argu);
//        System.out.println(type + " " + name);
        return type + " " + name;
    }

    /**
     * f0 -> "public"
     * f1 -> Type()
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( FormalParameterList() )?
     * f5 -> ")"
     * f6 -> "{"
     * f7 -> ( VarDeclaration() )*
     * f8 -> ( Statement() )*
     * f9 -> "return"
     * f10 -> Expression()
     * f11 -> ";"
     * f12 -> "}"
    **/
    public String visit(MethodDeclaration n, String argu) throws Exception {
        String retStr=null;
//        n.f0.accept(this, argu);
        String myType = n.f1.accept(this, argu);
        String myName = n.f2.accept(this, argu);
//        n.f1.accept(this, argu);
//        n.f2.accept(this, argu);
//        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
//        n.f5.accept(this, argu);
//        n.f6.accept(this, argu);
        n.f7.accept(this, argu);
        n.f8.accept(this, argu + "." + myName);
//        n.f9.accept(this, argu);
        String retval = n.f10.accept(this, argu + "." + myName);
        DataVisitor.methods.add(argu + "." + myName + "/" + myType + "/" + retval);//        n.f11.accept(this, argu);
//        n.f12.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> FormalParameter()
     * f1 -> FormalParameterTail()
     **/
    @Override
    public String visit(FormalParameterList n, String argu) throws Exception {
        String ret = n.f0.accept(this, argu);
        if (n.f1 != null) {
            ret += n.f1.accept(this, argu);
        }
        return ret;
    }

    /**
     * f0 -> FormalParameter()
     * f1 -> FormalParameterTail()
     **/
    public String visit(FormalParameterTerm n, String argu) throws Exception {
        return n.f1.accept(this, argu);
    }

    /**
     * f0 -> ","
     * f1 -> FormalParameter()
     **/
    @Override
    public String visit(FormalParameterTail n, String argu) throws Exception {
        String ret = "";
        for ( Node node: n.f0.nodes) {
            ret += ", " + node.accept(this, argu);
        }

        return ret;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     **/
    @Override
    public String visit(FormalParameter n, String argu) throws Exception{
        String type = n.f0.accept(this, argu);
//        if(!DataVisitor.simpleClasses.contains(type) && !type.equals("int") && !type.equals("int[]") && !type.equals("boolean")){
//            System.out.println("ERROR: undefined identifier: " + type);
//            throw new ParseException("ERROR");
//        }
        String name = n.f1.accept(this, argu);
        return  type + " " + name;
    }
    /**
     * f0 -> ArrayType()
     *       | BooleanType()
     *       | IntegerType()
     *       | Identifier()
     */
    public String visit(Type n, String argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    /**
     * f0 -> "int"
     * f1 -> "["
     * f2 -> "]"
     */
    public String visit(ArrayType n, String argu) {
        return "int[]";
    }

    /**
     * f0 -> "boolean"
     */
    public String visit(BooleanType n, String argu) {
        return "boolean";
    }


    /**
     * f0 -> "int"
     */
    public String visit(IntegerType n, String argu) {
        return "int";
    }

    /**
     * f0 -> Block()
     *       | AssignmentStatement()
     *       | ArrayAssignmentStatement()
     *       | IfStatement()
     *       | WhileStatement()
     *       | PrintStatement()
     */
    public String visit(Statement n, String argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    /**
     * f0 -> "{"
     * f1 -> ( Statement() )*
     * f2 -> "}"
     */
    public String visit(Block n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> Identifier()
     * f1 -> "="
     * f2 -> Expression()
     * f3 -> ";"
     */
    public String visit(AssignmentStatement n, String argu) throws Exception {
        String retStr=null;
        String[] crop = argu.split("[.]");
        String name = n.f0.accept(this, argu);
        String[] cropname = name.split("\\s");
//        System.out.println(argu+" " +name);
//        n.f1.accept(this, argu);
        String exp = n.f2.accept(this, argu);
//        if(!Helper.typeOfVariable(crop[0],argu,exp,name))
//        {
//            System.out.println("ERROR1: wrong assignment: incombatible types: " + name);
////            System.exit(1);
//            throw new ParseException("ERROR");
//        }
//        System.out.println(name+" = "+exp );
//        n.f3.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> Identifier()
     * f1 -> "["
     * f2 -> Expression()
     * f3 -> "]"
     * f4 -> "="
     * f5 -> Expression()
     * f6 -> ";"
     */
    public String visit(ArrayAssignmentStatement n, String argu) throws Exception {
        String retStr=null;
        String name = n.f0.accept(this, argu);
        String[] crop = argu.split("[.]");
//        System.out.println(name);
        n.f1.accept(this, argu);
        String index = n.f2.accept(this, argu);
        //check if index is integer
//        if(!index.equals("int")) {
//            System.out.println("ERROR: array index not integer");
////            System.exit(1);
//            throw new ParseException("ERROR");
//        }
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        String exp2 = n.f5.accept(this, argu);
        //check if expression assigned to array is integer and if identifier is integer array
//        if(!Helper.typeOfVariable(crop[0],argu,exp2+"[]",name))
//        {
//            System.out.println("ERROR2: wrong assignment: incombatible types: " + name);
////            System.exit(1);
//            throw new ParseException("ERROR");
//        }
//        System.out.println(exp2);
        n.f6.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> "if"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     * f5 -> "else"
     * f6 -> Statement()
     */
    public String visit(IfStatement n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        String iff = n.f2.accept(this, argu);
//        if(!iff.equals("boolean")){
//            System.out.println("ERROR: incompatible types: cannot be converted to boolean");
////            System.exit(1);
//            throw new ParseException("ERROR");
//        }
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        n.f5.accept(this, argu);
        n.f6.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> "while"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     */
    public String visit(WhileStatement n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        String whill = n.f2.accept(this, argu);
//        if(!whill.equals("boolean")){
//            System.out.println("ERROR: incompatible types: cannot be converted to boolean");
////            System.exit(1);
//            throw new ParseException("ERROR");
//        }
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> "System.out.println"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> ";"
     */
    public String visit(PrintStatement n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        String prnt = n.f2.accept(this, argu);
//        if(!prnt.equals("int")){
//            System.out.println("ERROR: incompatible types: cannot be converted to int");
////            System.exit(1);
//            throw new ParseException("ERROR");
//        }
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        return retStr;
    }

    /**
     * Expression -> AndExpression() OK epistrefei typeBoolean
     *       | CompareExpression() OK epistrefei typeBoolean
     *       | PlusExpression() OK epistrefei typeInt
     *       | MinusExpression() OK epistrefei typeInt
     *       | TimesExpression() OK epistrefei typeInt
     *       | ArrayLookup() OK epistrefei typeInt
     *       | ArrayLength() OK epistrefei typeInt
     *       | MessageSend() OK epistrefei ton typo methodou tha prepei na elegxei an h klhsh exei swsta orismata
     *       | PrimaryExpression()
     */
    public String visit(Expression n, String argu) throws Exception {
        String retStr=n.f0.accept(this, argu);
//        System.out.println(retStr);
        return retStr;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "&&"
     * f2 -> PrimaryExpression()
     */
    public String visit(AndExpression n, String argu) throws Exception {
        String retStr="boolean";
        String[] crop = argu.split("[.]");
        String arg1 = n.f0.accept(this, argu);
//        if(!arg1.equals("boolean")&&!Helper.typeOfVariable(crop[0],argu,"boolean",arg1) ){
//            System.out.println("ERROR: incompatible types: cannot be converted to boolean");
////            System.exit(1);
//            throw new ParseException("ERROR");
//        }
        n.f1.accept(this, argu);
        String arg2 = n.f2.accept(this, argu);
//        if(!arg2.equals("boolean")&&!Helper.typeOfVariable(crop[0],argu,"boolean",arg2) ){
//            System.out.println("ERROR: incompatible types: cannot be converted to boolean");
////            System.exit(1);
//            throw new ParseException("ERROR");
//        }
        return retStr;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "<"
     * f2 -> PrimaryExpression()
     */
    public String visit(CompareExpression n, String argu) throws Exception {
        String retStr="boolean";
        String[] crop = argu.split("[.]");
        String arg1 = n.f0.accept(this, argu);
//        if(!arg1.equals("int")&&!Helper.typeOfVariable(crop[0],argu,"int",arg1) ){
//            System.out.println("ERROR: incompatible types: cannot be converted to int");
////            System.exit(1);
//            throw new ParseException("ERROR");
//        }
        n.f1.accept(this, argu);
        String arg2 = n.f2.accept(this, argu);
//        if(!arg2.equals("int")&&!Helper.typeOfVariable(crop[0],argu,"int",arg2) ){
//            System.out.println("ERROR: incompatible types: cannot be converted to int");
////            System.exit(1);
//            throw new ParseException("ERROR");
//        }
        return retStr;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "+"
     * f2 -> PrimaryExpression()
     */
    public String visit(PlusExpression n, String argu) throws Exception {
        String retStr="int";
        String[] crop = argu.split("[.]");
        String arg1 = n.f0.accept(this, argu);
//        if(!arg1.equals("int")&&!Helper.typeOfVariable(crop[0],argu,"int",arg1) ){
//            System.out.println("ERROR: incompatible types: cannot be converted to int");
////            System.exit(1);
//            throw new ParseException("ERROR");
//        }
        n.f1.accept(this, argu);
        String arg2 = n.f2.accept(this, argu);
//        if(!arg2.equals("int")&&!Helper.typeOfVariable(crop[0],argu,"int",arg2) ){
//            System.out.println("ERROR: incompatible types: cannot be converted to int");
////            System.exit(1);
//            throw new ParseException("ERROR");
//        }
        return retStr;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "-"
     * f2 -> PrimaryExpression()
     */
    public String visit(MinusExpression n, String argu) throws Exception {
        String retStr="int";
        String[] crop = argu.split("[.]");
        String arg1 = n.f0.accept(this, argu);
//        if(!arg1.equals("int")&&!Helper.typeOfVariable(crop[0],argu,"int",arg1) ){
//            System.out.println("ERROR: incompatible types: cannot be converted to int");
////            System.exit(1);
//            throw new ParseException("ERROR");
//        }
        n.f1.accept(this, argu);
        String arg2 = n.f2.accept(this, argu);
//        if(!arg2.equals("int")&&!Helper.typeOfVariable(crop[0],argu,"int",arg2) ){
//            System.out.println("ERROR: incompatible types: cannot be converted to int");
////            System.exit(1);
//            throw new ParseException("ERROR");
//        }
        return retStr;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "*"
     * f2 -> PrimaryExpression()
     */
    public String visit(TimesExpression n, String argu) throws Exception {
        String retStr="int";
        String[] crop = argu.split("[.]");
        String arg1 = n.f0.accept(this, argu);
//        if(!arg1.equals("int")&&!Helper.typeOfVariable(crop[0],argu,"int",arg1) ){
//            System.out.println("ERROR: incompatible types: cannot be converted to int");
////            System.exit(1);
//            throw new ParseException("ERROR");
//        }
        n.f1.accept(this, argu);
        String arg2 = n.f2.accept(this, argu);
//        if(!arg2.equals("int")&&!Helper.typeOfVariable(crop[0],argu,"int",arg2) ){
//            System.out.println("ERROR: incompatible types: cannot be converted to int");
////            System.exit(1);
//            throw new ParseException("ERROR");
//        }
        return retStr;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "["
     * f2 -> PrimaryExpression()
     * f3 -> "]"
     */
    public String visit(ArrayLookup n, String argu) throws Exception {
        String retStr="int";
        //to f0 tha prepei na einai Identifier enos int array
        //tha prepei na epistrefw ton typo tou identifier(xwris to array) afou koitaksw to symbolTable
        String arg1 = n.f0.accept(this, argu);
        String[] crop = argu.split("[.]");
//        if(!arg1.equals("int[]")&&!Helper.typeOfVariable(crop[0],argu,"int[]",arg1)&&!arg1.equals("new int []"))
//        {
//            System.out.println("ERROR3: wrong assignment: incombatible types: " + arg1);
////            System.exit(1);
//            throw new ParseException("ERROR");
//        }
        n.f1.accept(this, argu);
        String arg2 = n.f2.accept(this, argu);
//        if(!arg2.equals("int")&&!Helper.typeOfVariable(crop[0],argu,"int",arg2) ){
//            System.out.println("ERROR: incompatible types: array index is not int");
////            System.exit(1);
//            throw new ParseException("ERROR");
//        }
        n.f3.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> "length"
     */
    public String visit(ArrayLength n, String argu) throws Exception {
        String retStr="int";
        String arg1 = n.f0.accept(this, argu);
        String[] crop = argu.split("[.]");
//        if(!arg1.equals("int[]")&&!Helper.typeOfVariable(crop[0],argu,"int[]",arg1)&&!arg1.equals("new int []"))
//        {
//            System.out.println("ERROR4: wrong assignment: incombatible types: " + arg1 + " is not an array");
////            System.exit(1);
//            throw new ParseException("ERROR");
//        }
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( ExpressionList() )?
     * f5 -> ")"
     */
    public String visit(MessageSend n, String argu) throws Exception {
        String retStr=null;
        //tha prepei na epistrefw ton typo epistrofhs ths methodou
        String arg1 = n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        String method = n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        String argList = n.f4.present() ? n.f4.accept(this, argu) : "";
        n.f5.accept(this, argu);
//        System.out.println("new");
//        System.out.println(argu+" "+arg1+"."+method+"("+argList+")");
        String[] crop = argu.split("[.]");


        retStr = Helper.getTypeOfMessage(crop[0],arg1+"."+method+"("+argList+")");
//        System.out.println("ret "+ retStr);
//        if(retStr==null) {
//            System.out.println("ERROR: messageSend");
////            System.exit(1);
//            throw new ParseException("ERROR");
//        }
//        if(retStr.equals("")){
//            System.out.println("ERROR: messageSend");
////            System.exit(1);
//            throw new ParseException("ERROR");
//        }
        return retStr;
    }


    //    ExpressionList 	::= 	Expression() ExpressionTail()
    /**
     * f0 -> 	Expression()
     * f1 -> 	ExpressionTail()
     **/
    @Override
    public String visit(ExpressionList n, String argu) throws Exception{
//        System.out.println();
//        super.visit(n, argu);
//        System.out.println();
        String ret = n.f0.accept(this, argu);
        if (n.f1 != null) {
            ret += n.f1.accept(this, argu);
        }
        return ret;
    }

//    ExpressionTail() 	::= 	"," Expression
    /**
     * f0 -> ( ExpressionTerm() )*
     */
    @Override
    public String visit(ExpressionTail n, String argu) throws Exception{
        String ret = "";
        for ( Node node: n.f0.nodes) {
            ret += ", " + node.accept(this, argu);
        }
        return ret;
    }

//    ExpressionTerm() 	::= 	"," Expression
    /**
     * f0 -> ","
     * f1 -> Expression()
     */
    @Override
    public String visit(ExpressionTerm n, String argu) throws Exception{
		return n.f1.accept(this, argu);
    }

    /**
     * f0 -> IntegerLiteral() OK epistrefei typeInt
     *       | TrueLiteral() OK epistrefei typeBoolean
     *       | FalseLiteral() OK epistrefei typeBoolean
     *       | Identifier() OK epistrefei id
     *       | ThisExpression() OK epistrefei this
     *       | ArrayAllocationExpression() OK θα πρεπει ο identifier na elegxei an yparxei tetoio class
     *                                        kai ua prepei na elgxetai apo to anwtero epipedo an ginetai swsto assignment
     *       | AllocationExpression() OK θα πρεπει ο identifier na elegxei an yparxei tetoio class
     *                                  kai ua prepei na elgxetai apo to anwtero epipedo an ginetai swsto assignment
     *       | NotExpression() OK epistrefei typeBoolean
     *       | BracketExpression() OK epistrefei typeBoolean na elegxei an einai boolean mesa
     */
    public String visit(PrimaryExpression n, String argu) throws Exception {
        String retStr = n.f0.accept(this, argu);
        String arg;
        String[] crop = argu.split("[.]");
//        if(retStr==null){
//            System.out.println("PrimaryExpression received null");
//            throw new ParseException("ERROR");
////            System.exit(1);
//        }
        if(!retStr.equals("int")&&!retStr.equals("boolean")) {
            arg = Helper.getTypeOfVariable(crop[0], argu, retStr);
            if(!arg.equals(""))
                return arg;
        }

        return retStr;
    }

    /**
     * f0 -> <INTEGER_LITERAL>
     */
    public String visit(IntegerLiteral n, String argu) throws Exception {
        if(Helper.isInt(n.f0.toString())==1)
            return "int";
        else{
            System.out.println("ERROR: invalid integer: "+ n.f0.toString());
            throw new ParseException("ERROR");
        }
    }

    /**
     * f0 -> "true"
     */
    public String visit(TrueLiteral n, String argu) throws Exception {
        return "boolean";
    }

    /**
     * f0 -> "false"
     */
    public String visit(FalseLiteral n, String argu) throws Exception {
        return "boolean";
    }

    /**
     * f0 -> <IDENTIFIER>
     */
    public String visit(Identifier n, String argu) throws Exception {
        return n.f0.toString();
    }

    /**
     * f0 -> "this"
     */
    public String visit(ThisExpression n, String argu) throws Exception {
        return n.f0.toString();
    }

    /**
     * f0 -> "new"
     * f1 -> "int"
     * f2 -> "["
     * f3 -> Expression()
     * f4 -> "]"
     */
    public String visit(ArrayAllocationExpression n, String argu) throws Exception {
        String retStr="new int []";
//        n.f0.accept(this, argu);
//        n.f1.accept(this, argu);
//        n.f2.accept(this, argu);
        String[] crop = argu.split("[.]");
        String arg = n.f3.accept(this, argu);
//        if(!arg.equals("int")&&!Helper.typeOfVariable(crop[0],argu,"int",arg) ){
//            System.out.println("ERROR: incompatible types: array index is not int");
////            System.exit(1);
//            throw new ParseException("ERROR");
//        }
//        n.f4.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> "new"
     * f1 -> Identifier()
     * f2 -> "("
     * f3 -> ")"
     */
    public String visit(AllocationExpression n, String argu) throws Exception {
//        n.f0.accept(this, argu);
        String name = n.f1.accept(this, argu);
        String retStr= "new " + name + " ()";
//        if(!name.equals("int")&&!name.equals("int[]")&&!name.equals("boolean")) and is not any class is error
//        n.f2.accept(this, argu);
//        n.f3.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> "!"
     * f1 -> PrimaryExpression()
     */
    public String visit(NotExpression n, String argu) throws Exception {
        String retStr="boolean";
        n.f0.accept(this, argu);
        String[] crop = argu.split("[.]");
        String arg = n.f1.accept(this, argu);
//        if(!arg.equals("boolean")&&!Helper.typeOfVariable(crop[0],argu,"boolean",arg) ){
//            System.out.println("ERROR: not boolean clause");
//            throw new ParseException("ERROR");
//        }
        return retStr;
    }

    /**
     * f0 -> "("
     * f1 -> Expression()
     * f2 -> ")"
     */
    public String visit(BracketExpression n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        String name = n.f1.accept(this, argu);
        retStr=name;
//        System.exit(1);
        n.f2.accept(this, argu);
        return retStr;
    }
}

class Generator extends GJDepthFirst<String, String>{

    public static String emit;
    public static Map<String, Integer> counter;
    public static int labcounter;
    public static Map<String, Map<String, String>> varRegs;
    public static Map<String, Map<String, String>> labels;

    public <K, V> K getKey(Map<K, V> map, V value) {
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (entry.getValue().equals(value)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public String ridOf(String str)
    {
        String[] temp = str.split("/");
        String ret = "";
        if(temp[temp.length-1].startsWith("class:")){
            for(int i=0;i<temp.length-1;i++)
            {
                if(i==0)
                    ret = temp[i];
                else
                    ret = ret + "/" + temp[i];
            }
        }
        else
            ret = str;
        return ret;
    }

    public static String getNewLabel(String argu, String variable)
    {
        int myCounter = 0;
        String[] temp = argu.split("/");
        if(temp[0].equals("load"))
            argu = temp[1];

        if (!counter.containsKey(argu))
            counter.put(argu, 0);
        else
            myCounter = counter.get(argu);
//        emit+="\n; my="+argu;
        //get label and increment the counter
        String label = "%_" + myCounter;
        counter.replace(argu, ++myCounter);

        //store label - variable tuple
        if(variable!=null)
        {
            Map<String, String> myVarRegs;
            if (!varRegs.containsKey(argu)) {
                myVarRegs = new TreeMap<>();
                varRegs.put(argu, myVarRegs);
            } else
                myVarRegs = varRegs.get(argu);
            myVarRegs.put(label, variable);
        }
        return label;
    }

    public String getNewExprLabel(String argu, String variable)
    {
        //get label and increment the counter
        String label = "exp_res_" + labcounter++;
        return label;
    }

    public String getLastLabel(String argu)
    {
        if (!counter.containsKey(argu))
            return null;
        else
            return "%_" + (counter.get(argu)-1);
    }

    public String getVariableRegister(String argu, String variable)
    {
        String label;
        Map<String, String> myVarRegs;
        String[] temp = argu.split("/");
        if(temp[0].equals("load"))
            argu = temp[1];
        if (!varRegs.containsKey(argu)) {
            myVarRegs = new TreeMap<>();
            varRegs.put(argu, myVarRegs);
        }
        else
            myVarRegs = varRegs.get(argu);

        //if label for variable exists get it
        if(myVarRegs.containsValue(variable)) {
            label = getKey(myVarRegs, variable);
            myVarRegs.remove(label);
            //myVarRegs.put(label, variable);
        }
        //or else create a new one and get it
        else
            label = getNewLabel(argu, variable);
        return label;
    }

    public static boolean labelExists(String argu, String variable)
    {
        Map<String, String> myVarRegs;
        if(argu==null)
            return false;
        if (!varRegs.containsKey(argu)) {
            myVarRegs = new TreeMap<>();
            varRegs.put(argu, myVarRegs);
        }
        else
            myVarRegs = varRegs.get(argu);

        if(myVarRegs.containsValue(variable))
            return true;
        return false;
    }

    public String getIt(String argu, String variable) throws Exception
    {
        String[] temp = variable.split("\\s");
        String care1 = "";
        String care2 = "";
        //if the string contains type find it and erase it
        for(int i=0;i<temp.length;i++) {
            if (!temp[i].equals("") && care1.equals(""))
                care1 = temp[i];
            else if (!temp[i].equals("") && care2.equals(""))
                care2 = temp[i];
        }
        if(care1.startsWith("i") && care1.length()>1 && Helper.isInt(Character.toString(care1.charAt(1)))==1 && !care2.equals(""))
            variable = care2;
        //if the string is not a register
        if(Helper.isVariableRegister(variable)==0){
            //if its not an int, get the loaded variable
            if(Helper.isInt(variable)==0 && labelExists(argu, variable))
                variable = getVariableRegister(argu, variable);
            //or load it
            else if(Helper.isInt(variable)==0 && !labelExists(argu, variable))
                variable = Helper.doLoad(argu, variable);
        }
        //if the string is a register with type
        if(Helper.isVariableRegister(variable)==2){
            temp = variable.split("\\s");
            for(int i=0;i<temp.length;i++) {
                if (!temp[i].equals("") && care1.equals(""))
                    care1 = temp[i];
                else if (!temp[i].equals("") && care2.equals(""))
                    care2 = temp[i];
            }
            if(care1.startsWith("i") && care1.length()>1 && Helper.isInt(Character.toString(care1.charAt(1)))==1 && !care2.equals(""))
                variable = care2;
        }
        return variable;
    }

    /**
     * f0 -> MainClass()
     * f1 -> ( TypeDeclaration() )*
     * f2 -> <EOF>
     */
    public String visit(Goal n, String argu) throws Exception {
        String retStr=null;
        emit = "";
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> "public"
     * f4 -> "static"
     * f5 -> "void"
     * f6 -> "main"
     * f7 -> "("
     * f8 -> "String"
     * f9 -> "["
     * f10 -> "]"
     * f11 -> Identifier()
     * f12 -> ")"
     * f13 -> "{"
     * f14 -> ( VarDeclaration() )*
     * f15 -> ( Statement() )*
     * f16 -> "}"
     * f17 -> "}"
     */
    public String visit(MainClass n, String argu) throws Exception {
        String retStr=null;
        //        n.f0.accept(this, argu);
        emit += "\ndefine i32 @main() {";
        String classname = n.f1.accept(this, argu);
//        n.f2.accept(this, argu);
//        n.f3.accept(this, argu);
//        n.f4.accept(this, argu);
//        n.f5.accept(this, argu);
//        n.f6.accept(this, argu);
//        n.f7.accept(this, argu);
//        n.f8.accept(this, argu);
//        n.f9.accept(this, argu);
//        n.f10.accept(this, argu);
//        n.f11.accept(this, argu);
//        n.f12.accept(this, argu);
//        n.f13.accept(this, argu);
        n.f14.accept(this, classname);
        n.f15.accept(this, classname);
//        n.f16.accept(this, argu);
//        n.f17.accept(this, argu);
//        System.out.println("file");
        emit += "\n\tret i32 0\n}";
        return retStr;
    }

    /**
     * f0 -> ClassDeclaration()
     *       | ClassExtendsDeclaration()
     */
    public String visit(TypeDeclaration n, String argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> ( VarDeclaration() )*
     * f4 -> ( MethodDeclaration() )*
     * f5 -> "}"
     */
    public String visit(ClassDeclaration n, String argu) throws Exception {
        String retStr=null;
//        n.f0.accept(this, argu);
        String classname = n.f1.accept(this, argu);
//        n.f2.accept(this, argu);
//        n.f3.accept(this, argu);
        n.f4.accept(this, classname);
//        n.f5.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "extends"
     * f3 -> Identifier()
     * f4 -> "{"
     * f5 -> ( VarDeclaration() )*
     * f6 -> ( MethodDeclaration() )*
     * f7 -> "}"
     */
    public String visit(ClassExtendsDeclaration n, String argu) throws Exception {
        String retStr=null;
//        n.f0.accept(this, argu);
//        n.f1.accept(this, argu);
        String classname = n.f1.accept(this, argu);
//        n.f2.accept(this, argu);
        String extendOf = " extends " + n.f3.accept(this, argu);
//        n.f4.accept(this, argu);
//        n.f5.accept(this, argu);
        n.f6.accept(this, classname+extendOf);
//        n.f7.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     * f2 -> ";"
     */
    public String visit(VarDeclaration n, String argu) throws Exception{
        String type = n.f0.accept(this, argu);
//        if(!DataVisitor.simpleClasses.contains(type) && !type.equals("int") && !type.equals("int[]") && !type.equals("boolean")){
//            System.out.println("ERROR: undefined identifier: " + type);
//            throw new ParseException("ERROR");
//        }

        String name = n.f1.accept(this, argu);
        n.f2.accept(this, argu);
//        System.out.println(type +" "+name);
        if(DataVisitor.simpleClasses.contains(type))
            emit += "\n\t%" + String.format("%s = alloca i8*",name);
        if(type.equals("int"))
            emit += "\n\t%" + String.format("%s = alloca i32",name);
        if(type.equals("int[]"))
            emit += "\n\t%" + String.format("%s = alloca i32*",name);
        if(type.equals("boolean"))
            emit += "\n\t%" + String.format("%s = alloca i1",name);
//        System.out.println(type + " " + name);
        return type + " " + name;
    }

    /**
     * f0 -> "public"
     * f1 -> Type()
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( FormalParameterList() )?
     * f5 -> ")"
     * f6 -> "{"
     * f7 -> ( VarDeclaration() )*
     * f8 -> ( Statement() )*
     * f9 -> "return"
     * f10 -> Expression()
     * f11 -> ";"
     * f12 -> "}"
     **/
    public String visit(MethodDeclaration n, String argu) throws Exception {
        String retStr=null;
        int counter = 0;
        String paremit = "";
//        n.f0.accept(this, argu);
        String myType = n.f1.accept(this, argu);
        String myName = n.f2.accept(this, argu);
        String temp[] = argu.split("\\s");
        String rettype = "";
        //write method name and parameters
        emit += "\n\ndefine ";
        if(DataVisitor.simpleClasses.contains(myType)){
            emit += "i8*";
            rettype = "i8* ";}
        else if(myType.equals("int")){
            emit += "i32";
            rettype = "i32 ";}
        else if(myType.equals("int[]")){
            emit += "i32*";
            rettype = "i32* ";}
        else if(myType.equals("boolean")){
            emit += "i1";
            rettype = "i1 ";}
//        for(int i=0;i<temp.length;i++)
//            System.out.println(temp[i]);
        if(temp.length<=2)
            emit += String.format(" @%s.%s(i8* %%this",argu,myName);
        else
            emit += String.format(" @%s.%s(i8* %%this",temp[0],myName);

//        n.f1.accept(this, argu);
//        n.f2.accept(this, argu);
//        n.f3.accept(this, argu);
        String parameters = n.f4.present() ? n.f4.accept(this, argu + "." + myName) : "";
        String[] arg = parameters.split(",");
        //deal with the parameters (alloca, store)
        if(!parameters.equals(""))
            for(int i=0; i<arg.length;i++) {
                String[] arg2 = arg[i].trim().split("\\s");
                if(DataVisitor.simpleClasses.contains(arg2[0])) {
                    emit += ", i8*";
                    paremit += "\n\t%"+String.format("%s = alloca i8*",arg2[1]);
                    paremit += "\n\tstore i8* %" + String.format(".%s, i8** ",arg2[1]) + "%" + arg2[1];
                }
                else if(arg2[0].equals("int")) {
                    emit += ", i32";
                    paremit += "\n\t%"+String.format("%s = alloca i32",arg2[1]);
                    paremit += "\n\tstore i32 %" + String.format(".%s, i32* ",arg2[1]) + "%" + arg2[1];
                }
                else if(arg2[0].equals("int[]")) {
                    emit += ", i32*";
                    paremit += "\n\t%"+String.format("%s = alloca i32*",arg2[1]);
                    paremit += "\n\tstore i32* %" + String.format(".%s, i32** ",arg2[1]) + "%" + arg2[1];
                }
                else if(arg2[0].equals("boolean")) {
                    emit += ", i1";
                    paremit += "\n\t%"+String.format("%s = alloca i1",arg2[1]);
                    paremit += "\n\tstore i1 %" + String.format(".%s, i1* ",arg2[1]) + "%" + arg2[1];
                }
                emit += " %." + arg2[1];
            }
        emit += ") {" + paremit;
//        n.f5.accept(this, argu);
//        n.f6.accept(this, argu);
        //emit += "\n; VarDeclaration";
        n.f7.accept(this, argu + "." + myName);
        //emit += "\n; Statement";
        n.f8.accept(this, argu + "." + myName);
//        n.f9.accept(this, argu);

        //take in return value
        String retval = n.f10.accept(this, argu + "." + myName);
        retval = ridOf(retval.trim());
        DataVisitor.methods.add(argu + "." + myName + "/" + myType + "/" + retval);
//        n.f11.accept(this, argu);
//        n.f12.accept(this, argu);

        String[] tempstore = retval.trim().split("/");
        //if return value is a new allocation
        if(tempstore[0].equals("store"))
        {
            if(tempstore[1].equals("object")) {
                retval = "i8* "+tempstore[2];
            }
            if(tempstore[1].equals("int[]")) {
                retval = "i32* "+tempstore[2];
            }
        }
        //if return value is not a register
        else if(Helper.isVariableRegister(retval)==0)
        {
//            emit += "\n;"+retval+" is ";
            //if its an integer with type
            if (Helper.isInt(retval) == 2) {
//                emit += "int";
                retval = retval;
            }
            //if its an integer without type
            else if (Helper.isInt(retval) == 1) {
//                emit += "int";
                retval = rettype + retval;
            }
            else if (Helper.isInt(retval) == 0) {
//                emit += "var reg";
                String type = Helper.getTypeOfVariable(argu, argu+"."+myName, retval);
                String[] tempVar = type.split("\\s");
                if(DataVisitor.simpleClasses.contains(tempVar[0])) {
                    retval = "i8* "+getVariableRegister(argu+"."+myName, retval);
                }else if(tempVar[0].equals("int")) {
                    retval = "i32 "+getVariableRegister(argu+"."+myName, retval);
                }else if(tempVar[0].equals("int[]")) {
                    retval = "i32* "+getVariableRegister(argu+"."+myName, retval);
                }else if(tempVar[0].equals("boolean")) {
                    retval = "i1 "+getVariableRegister(argu+"."+myName, retval);
                }
            }
            //if its a field of the class
            else if(Helper.isField(argu, retval)>=0){
//                emit += "field";
                String getelementptr = getNewLabel(argu+"."+myName, null);
                String bitcast = getNewLabel(argu+"."+myName, null);
                emit += "\n\t" + getelementptr + " = getelementptr i8, i8* %this, i32 " + (Helper.isField(argu, retval)+8);
                emit += "\n\t" + bitcast + " = bitcast i8* "+getelementptr+" to ";
                String type = Helper.getTypeOfVariable(argu, argu+"."+myName, retval);
                String[] tempVar = type.split("\\s");
                if(DataVisitor.simpleClasses.contains(tempVar[0])) {
                    emit += "i8* ";
                    retval = "i8* "+bitcast;
                }else if(tempVar[0].equals("int")) {
                    emit += "i32 ";
                    retval = "i32 "+bitcast;
                }else if(tempVar[0].equals("int[]")) {
                    emit += "i32* ";
                    retval = "i32* "+bitcast;
                }else if(tempVar[0].equals("boolean")) {
                    emit += "i1 ";
                    retval = "i1 "+bitcast;
                }
            }
            //if its not a field of the class, thus a variable declared in the method
            else if(Helper.isField(argu, retval)<0 || Helper.isMethodVariable(argu, retval)){
//                emit += " is method var";
                String type = Helper.getTypeOfVariable(argu, argu+"."+myName, retval);
                String[] tempVar = type.split("\\s");
                String load = getNewLabel(argu, null);
                emit += "\n\t" + load + " = load ";
                if(DataVisitor.simpleClasses.contains(tempVar[0])) {
                    emit += "i8*, i8** %"+retval;
                    retval = "i8* "+load;
                }else if(tempVar[0].equals("int")) {
                    emit += "i32, i32* %"+retval;
                    retval = "i32 "+load;
                }else if(tempVar[0].equals("int[]")) {
                    emit += "i32*, i32** %"+retval;
                    retval = "i32* "+load;
                }else if(tempVar[0].equals("boolean")) {
                    emit += "i1, i1* %"+retval;
                    retval = "i1 "+load;
                }
            }
        }
        else if(Helper.isVariableRegister(retval)==1)
            retval = rettype+retval;
        emit += "\n\tret " + retval.trim();
        emit += "\n}";
        return retStr;
    }

    /**
     * f0 -> FormalParameter()
     * f1 -> FormalParameterTail()
     **/
    @Override
    public String visit(FormalParameterList n, String argu) throws Exception {
        String ret = n.f0.accept(this, argu);
        if (n.f1 != null) {
            ret += n.f1.accept(this, argu);
        }
        return ret;
    }

    /**
     * f0 -> FormalParameter()
     * f1 -> FormalParameterTail()
     **/
    public String visit(FormalParameterTerm n, String argu) throws Exception {
        return n.f1.accept(this, argu);
    }

    /**
     * f0 -> ","
     * f1 -> FormalParameter()
     **/
    @Override
    public String visit(FormalParameterTail n, String argu) throws Exception {
        String ret = "";
        for ( Node node: n.f0.nodes) {
            ret += ", " + node.accept(this, argu);
        }

        return ret;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     **/
    @Override
    public String visit(FormalParameter n, String argu) throws Exception{
        String type = n.f0.accept(this, argu);
//        if(!DataVisitor.simpleClasses.contains(type) && !type.equals("int") && !type.equals("int[]") && !type.equals("boolean")){
//            System.out.println("ERROR: undefined identifier: " + type);
//            throw new ParseException("ERROR");
//        }
        String name = n.f1.accept(this, argu);
        return  type + " " + name;
    }
    /**
     * f0 -> ArrayType()
     *       | BooleanType()
     *       | IntegerType()
     *       | Identifier()
     */
    public String visit(Type n, String argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    /**
     * f0 -> "int"
     * f1 -> "["
     * f2 -> "]"
     */
    public String visit(ArrayType n, String argu) {
        return "int[]";
    }

    /**
     * f0 -> "boolean"
     */
    public String visit(BooleanType n, String argu) {
        return "boolean";
    }


    /**
     * f0 -> "int"
     */
    public String visit(IntegerType n, String argu) {
        return "int";
    }

    /**
     * f0 -> Block()
     *       | AssignmentStatement()
     *       | ArrayAssignmentStatement()
     *       | IfStatement()
     *       | WhileStatement()
     *       | PrintStatement()
     */
    public String visit(Statement n, String argu) throws Exception {
        String retStr = n.f0.accept(this, argu);
//        System.out.println(retStr);
        return retStr;
    }

    /**
     * f0 -> "{"
     * f1 -> ( Statement() )*
     * f2 -> "}"
     */
    public String visit(Block n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        return retStr;
    }

    /**
     * f0 -> Identifier()
     * f1 -> "="
     * f2 -> Expression()
     * f3 -> ";"
    **/
    public String visit(AssignmentStatement n, String argu) throws Exception {
        String retStr=null;
        String[] crop = argu.split("[.]");
        String name = n.f0.accept(this, argu);
        name = ridOf(name);
        String[] cropname = name.split("\\s");
//        System.out.println(argu+" " +name);
//        n.f1.accept(this, argu);
        String exp = n.f2.accept(this, argu);
        exp = ridOf(exp);
        System.out.println(argu+" " +name+" "+exp);
        String[] temp = exp.split("/");
//        System.out.println("crop = "+crop[0]+" "+argu);
        //if expression is a new allocation fo the thing
        if(temp[0].equals("store"))
        {
            int isField = 0;
            emit += "\n; store value: "+temp[2]+" into variable: "+name+" argu:"+argu;
            //if variable is declared in method
            if(Helper.isMethodVariable(argu, name)){
//            emit += "\n;"+name+" is method var of "+argu;
                String type = Helper.getTypeOfVariable(crop[0], argu, name);
                String[] tempVar = type.split("\\s");
                if(DataVisitor.simpleClasses.contains(tempVar[0])) {
                    name = "i8** %"+name;
                }else if(tempVar[0].equals("int")) {
                    name = "i32* %"+name;
                }else if(tempVar[0].equals("int[]")) {
                    name = "i32** %"+name;
                }else if(tempVar[0].equals("boolean")) {
                    name = "i1* %"+name;
                }
            }
            //if variable is field of object
            else if(Helper.isField(argu, name)>=0){
                isField = 1;
                String getelementptr = getNewLabel(argu, null);
                String bitcast = getNewLabel(argu, null);
                emit += "\n\t" + getelementptr + " = getelementptr i8, i8* %this, i32 " + (Helper.isField(argu, name)+8);
                emit += "\n\t" + bitcast + " = bitcast i8* "+getelementptr+" to ";
                String type = Helper.getTypeOfVariable(crop[0], null, name);
                String[] tempVar = type.split("\\s");
                if(DataVisitor.simpleClasses.contains(tempVar[0])) {
                    emit += "i8** ";
                    name = "i8** "+bitcast;
                }if(tempVar[0].equals("int")) {
                    emit += "i32* ";
                    name = "i32* "+bitcast;
                }if(tempVar[0].equals("int[]")) {
                    emit += "i32** ";
                    name = "i32** "+bitcast;
                }if(tempVar[0].equals("boolean")) {
                    emit += "i1* ";
                    name = "i1* "+bitcast;
                }
            }
            //do the store
            if(temp[1].equals("object")) {
                exp = temp[2];
                if(Helper.isVariableRegister(name)==2)
                    emit += "\n\tstore i8* " + temp[2] + ", " + name;
                else
                    emit += "\n\tstore i8* " + temp[2] + ", i8** %" + name;
                return name;
            }
            else if(temp[1].equals("int[]")) {
                exp = temp[2];
                if(Helper.isVariableRegister(name)==2)
                    emit += "\n\tstore i32* " + temp[2] + ", " + name;
                else
                    emit += "\n\tstore i32* " + temp[2] + ", i32** %" + name;
                return name;
            }
        }

        emit += "\n; assign "+exp+" to "+ name;
        //if expression is not a register
        if(Helper.isVariableRegister(exp)==0) {
//            emit += "\n;"+exp+" is ";
            //if its 'this'
            if(exp.equals("this")){
                exp = "i8* %this";
            }
            //if its int without type
            else if (Helper.isInt(exp) == 1) {
//                emit += "int";
                String type = Helper.getTypeOfVariable(crop[0], argu, exp);
                String[] tempVar = type.split("\\s");
                if(DataVisitor.simpleClasses.contains(tempVar[0])) {
                    exp = "i8* "+exp;
                }else if(tempVar[0].equals("int")) {
                    exp = "i32 "+exp;
                }else if(tempVar[0].equals("int[]")) {
                    exp = "i32* "+exp;
                }else if(tempVar[0].equals("boolean")) {
                    exp = "i1 "+exp;
                }
            }
            //if its int with type
            else if (Helper.isInt(exp) == 2) {
//                emit += "into";
                exp = exp;
            }
            //elsewise
            else if (Helper.isInt(exp) == 0) {
//                emit += "var reg";
                String type = Helper.getTypeOfVariable(crop[0], argu, exp);
                String[] tempVar = type.split("\\s");
                if(DataVisitor.simpleClasses.contains(tempVar[0])) {
                    exp = "i8* "+getVariableRegister(argu, exp);
                }else if(tempVar[0].equals("int")) {
                    exp = "i32 "+getVariableRegister(argu, exp);
                }else if(tempVar[0].equals("int[]")) {
                    exp = "i32* "+getVariableRegister(argu, exp);
                }else if(tempVar[0].equals("boolean")) {
                    exp = "i1 "+getVariableRegister(argu, exp);
                }
            }
            //if its variable declared in method
            else if(Helper.isMethodVariable(argu, name)){
//                emit += " is method var";
                String type = Helper.getTypeOfVariable(crop[0], argu, exp);
                String[] tempVar = type.split("\\s");
                String load = getNewLabel(argu, null);

                emit += "\n\t" + load + " = load ";
                if(DataVisitor.simpleClasses.contains(tempVar[0])) {
                    emit += "i8*, i8** %"+exp;
                    exp = "i8* "+load;
                }else if(tempVar[0].equals("int")) {
                    emit += "i32, i32* %"+exp;
                    exp = "i32 "+load;
                }else if(tempVar[0].equals("int[]")) {
                    emit += "i32*, i32** %"+exp;
                    exp = "i32* "+load;
                }else if(tempVar[0].equals("boolean")) {
                    emit += "i1, i1* %"+exp;
                    exp = "i1 "+load;
                }
            }
            //if its a field of the object
            else if(Helper.isField(argu, exp)>=0){
//                emit += "field";
                String getelementptr = getNewLabel(argu, null);
                String bitcast = getNewLabel(argu, null);
                emit += "\n\t" + getelementptr + " = getelementptr i8, i8* %this, i32 " + (Helper.isField(argu, exp)+8);
                emit += "\n\t" + bitcast + " = bitcast i8* "+getelementptr+" to ";
                String type = Helper.getTypeOfVariable(crop[0], null, exp);
                String[] tempVar = type.split("\\s");
                if(DataVisitor.simpleClasses.contains(tempVar[0])) {
                    emit += "i8* ";
                    exp = "i8* "+bitcast;
                }else if(tempVar[0].equals("int")) {
                    emit += "i32 ";
                    exp = "i32 "+bitcast;
                }else if(tempVar[0].equals("int[]")) {
                    emit += "i32* ";
                    exp = "i32* "+bitcast;
                }else if(tempVar[0].equals("boolean")) {
                    emit += "i1 ";
                    exp = "i1 "+bitcast;
                }
            }
        }

//        emit += "\n; on to " + name;
        //if identifier is variable declared in method
        if(Helper.isMethodVariable(argu, name)){
            emit += "\n;"+name+" is method var of "+argu;
            String type = Helper.getTypeOfVariable(crop[0], argu, name);
            String[] tempVar = type.split("\\s");
            if(DataVisitor.simpleClasses.contains(tempVar[0])) {
                name = "i8** %"+name;
            }else if(tempVar[0].equals("int")) {
                name = "i32* %"+name;
            }else if(tempVar[0].equals("int[]")) {
                name = "i32** %"+name;
            }else if(tempVar[0].equals("boolean")) {
                name = "i1* %"+name;
            }
        }
        //if identifier is field of object
        else if(Helper.isField(argu, name)>=0){
            emit += "\n;"+name+" is field "+Helper.isField(argu, name);
            String getelementptr = getNewLabel(argu, null);
            String bitcast = getNewLabel(argu, null);
            emit += "\n\t" + getelementptr + " = getelementptr i8, i8* %this, i32 " + (Helper.isField(argu, name)+8);
            emit += "\n\t" + bitcast + " = bitcast i8* "+getelementptr+" to ";
            String type = Helper.getTypeOfVariable(crop[0], null, name);
            String[] tempVar = type.split("\\s");
            if(DataVisitor.simpleClasses.contains(tempVar[0])) {
                emit += "i8** ";
                name = "i8** "+bitcast;
            }if(tempVar[0].equals("int")) {
                emit += "i32* ";
                name = "i32* "+bitcast;
            }if(tempVar[0].equals("int[]")) {
                emit += "i32** ";
                name = "i32** "+bitcast;
            }if(tempVar[0].equals("boolean")) {
                emit += "i1* ";
                name = "i1* "+bitcast;
            }
        }

        if(!Helper.hasType(exp)){
            emit += "\n; "+exp+" has no type";
            String[] tempVar = name.split("\\s");
            exp = tempVar[0].substring(0, tempVar[0].length()-1).trim()+" " + exp.trim();
        }
        else if(!Helper.hasType(name)){
            emit += "\n; "+name+" has no type";
            String[] tempVar = exp.split("\\s");
            name = tempVar[0].trim()+"* " + name.trim();
        }

        emit += "\n\tstore "+ exp + ", " + name;
        if(retStr!=null)
            retStr=retStr.trim();
        return retStr;
    }

    /**
     * f0 -> Identifier()
     * f1 -> "["
     * f2 -> Expression()
     * f3 -> "]"
     * f4 -> "="
     * f5 -> Expression()
     * f6 -> ";"
     */
    public String visit(ArrayAssignmentStatement n, String argu) throws Exception {
        String retStr=null;
        String name = n.f0.accept(this, argu);
        name = ridOf(name);

        String[] crop = argu.split("[.]");
//        System.out.println(name);
        n.f1.accept(this, argu);
        String index = n.f2.accept(this, argu);
        index = ridOf(index);

        //check if index is integer
//        if(!index.equals("int")) {
//            System.out.println("ERROR: array index not integer");
////            System.exit(1);
//            throw new ParseException("ERROR");
//        }
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        String exp2 = n.f5.accept(this, argu);
        exp2 = ridOf(exp2);

        emit += "\n; store value: "+exp2+" into element: "+index+" of array: "+name;

        String reg0 = getNewLabel(argu, null);
        String reg1 = getNewLabel(argu, null);
        String reg2 = getNewLabel(argu, null);
        String reg3 = getNewLabel(argu, null);
        String reg4 = getNewLabel(argu, null);
        String reg5 = getNewLabel(argu, null);
        String reg6 = getNewLabel(argu, null);
        String oob_ok_Label = getNewExprLabel(argu, null);
        String oob_err_Label = getNewExprLabel(argu, null);

        index = getIt(argu, index);
        exp2 = getIt(argu, exp2);

        //if array is field of object
        if(Helper.isField(argu, name)>=0){
            String getelementptr = getNewLabel(argu, null);
            String bitcast = getNewLabel(argu, null);
            emit += "\n\t" + getelementptr + " = getelementptr i8, i8* %this, i32 " + (Helper.isField(argu, name)+8);
            emit += "\n\t" + bitcast + " = bitcast i8* "+getelementptr+" to ";
            String type = Helper.getTypeOfVariable(crop[0], null, name);
            String[] tempVar = type.split("\\s");
            if(tempVar[0].equals("int[]")) {
                emit += "i32** ";
                name = "i32** "+bitcast;
                emit += "\n\tload i32* "  + ", " + name;
            }
        }

        if(Helper.isVariableRegister(name)==2)
            emit += "\n\t"+reg0+" = load i32*, "+name;
        else
            emit += "\n\t"+reg0+" = load i32*, i32** %"+name;
        emit += "\n\t"+reg1+" = load i32, i32* "+reg0;
        emit += "\n\t"+reg2+" = icmp sge i32 "+index+", 0";
        emit += "\n\t"+reg3+" = icmp slt i32 "+index+", "+reg1;
        emit += "\n\t"+reg4+" = and i1 "+reg2+", "+reg3;
        emit += "\n\tbr i1 "+reg4+", label %"+oob_ok_Label+", label %"+oob_err_Label;
        emit += "\n"+oob_err_Label+":";
        emit += "\n\tcall void @throw_oob()";
        emit += "\n\tbr label %"+oob_ok_Label;
        emit += "\n"+oob_ok_Label+":";
        emit += "\n\t"+reg5+" = add i32 1, "+index;
        emit += "\n\t"+reg6+" = getelementptr i32, i32* "+reg0+", i32 "+reg5;
        emit += "\n\tstore i32 "+exp2+", i32* "+reg6;

        n.f6.accept(this, argu);
        retStr = reg6;
        if(retStr!=null)
            retStr=retStr.trim();
        return "store/"+retStr;
    }

    /**
     * f0 -> "if"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     * f5 -> "else"
     * f6 -> Statement()
     */
    public String visit(IfStatement n, String argu) throws Exception {
        String retStr=null;
        emit += "\n; if...else statement";
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        emit += "\n; condition";
        String Expression = n.f2.accept(this, argu);
        Expression = ridOf(Expression);
        String[] temptype = Expression.split("\\s");
        Expression = getIt(argu, Expression);
        int isint=0;
        String result3 = getNewLabel(argu, null);
        if(temptype[0].equals("i32")){
            isint=1;
            emit += "\n; convert integer condition: "+temptype[0]+" to boolean expression: [true<>0/false==0]";
            String result1 = getNewLabel(argu, null);
            emit += "\n\t"+result1+" = icmp slt i32 "+Expression+", 0";
            String result2 = getNewLabel(argu, null);
            emit += "\n\t"+result2+" = icmp slt i32 0, "+Expression;
            emit += "\n\t"+result3+" = or i1 "+result1+", "+result2;
        }

        String if_else = getNewExprLabel(argu, null);
        String if_then = getNewExprLabel(argu, null);
        String if_end = getNewExprLabel(argu, null);
        if(isint==0)
            emit += "\n\tbr i1 "+Expression+", label %"+if_then+", label %"+if_else;
        else
            emit += "\n\tbr i1 "+result3+", label %"+if_then+", label %"+if_else;

        emit += "\n; else";
        emit += "\n"+if_else+":";
        //Else
        n.f6.accept(this, argu);
        emit += "\n\tbr label %"+if_end;
        emit += "\n; then";
        emit += "\n"+if_then+":";
        //Then
        n.f4.accept(this, argu);
        emit += "\n\tbr label %"+if_end;
        emit += "\n"+if_end+":";
        n.f3.accept(this, argu);
        n.f5.accept(this, argu);
        emit += "\n; end of if...else statement";
        if(retStr!=null)
            retStr=retStr.trim();
        return retStr;
    }

    /**
     * f0 -> "while"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     */
    public String visit(WhileStatement n, String argu) throws Exception {
        String retStr=null;
        emit += "\n; while loop";
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);

        String header = getNewExprLabel(argu, null);
        String loop_body = getNewExprLabel(argu, null);
        String exit_block = getNewExprLabel(argu, null);

        emit += "\n\t" + "br label %" + header;
        emit += "\n" + header + ":";
        String Condition = n.f2.accept(this, argu);
        Condition = ridOf(Condition);
        Condition = getIt(argu, Condition);
        n.f3.accept(this, argu);

        emit += "\n\t" + "br i1 "+Condition+", label %" + loop_body + ", label %" + exit_block;
        emit += "\n" + loop_body + ":";
        //Statement
        n.f4.accept(this, argu);
        emit += "\n\t" + "br label %" + header;
        emit += "\n" + exit_block + ":";
        emit += "\n; end of while loop";

        if(retStr!=null)
            retStr=retStr.trim();
        return retStr;
    }

    /**
     * f0 -> "System.out.println"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> ";"
     */
    public String visit(PrintStatement n, String argu) throws Exception {
        String retStr=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        String prnt = n.f2.accept(this, argu);
        prnt = ridOf(prnt);
        prnt = getIt(argu, prnt);

        emit += "\n; print integer "+prnt;

        emit += "\n\tcall void (i32) @print_int(i32 " + prnt + ")";
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        if(retStr!=null)
            retStr=retStr.trim();
        return retStr;    }

    /**
     * Expression -> AndExpression() OK epistrefei typeBoolean
     *       | CompareExpression() OK epistrefei typeBoolean
     *       | PlusExpression() OK epistrefei typeInt
     *       | MinusExpression() OK epistrefei typeInt
     *       | TimesExpression() OK epistrefei typeInt
     *       | ArrayLookup() OK epistrefei typeInt
     *       | ArrayLength() OK epistrefei typeInt
     *       | MessageSend() OK epistrefei ton typo methodou tha prepei na elegxei an h klhsh exei swsta orismata
     *       | PrimaryExpression()
     */
    public String visit(Expression n, String argu) throws Exception {
        String retStr=n.f0.accept(this, argu);
        //retStr = ridOf(retStr);
//        System.out.println("Expression: "+retStr);
        if(retStr!=null)
            retStr=retStr.trim();
        return retStr;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "&&"
     * f2 -> PrimaryExpression()
     */
    public String visit(AndExpression n, String argu) throws Exception {
        String retStr="boolean";
        emit += "\n; do logic AND operation between two booleans";

        String arg1 = n.f0.accept(this, argu);
        arg1 = ridOf(arg1);

        n.f1.accept(this, argu);

        String label0 = getNewExprLabel(argu, null);
        String label1 = getNewExprLabel(argu, null);
        String label2 = getNewExprLabel(argu, null);
        String label3 = getNewExprLabel(argu, null);
        String result = getNewLabel(argu, null);

        arg1 = getIt(argu, arg1);

        emit += "\n\tbr i1 "+arg1+", label %"+label1+", label %" + label0;
        emit += "\n" + label0 + ":";
        emit += "\n\tbr label %" + label3;
        emit += "\n" + label1 + ":";

        String arg2 = n.f2.accept(this, argu);
        arg2 = ridOf(arg2);
        arg2 = getIt(argu, arg2);

        emit += "\n\tbr label %" + label2;
        emit += "\n" + label2 + ":";
        emit += "\n\tbr label %" + label3;
        emit += "\n" + label3 + ":";
        emit += "\n\t"+result+" = phi i1 [ 0, %"+label0+" ], [ "+arg2+", %"+label2+" ]";

        if(result!=null)
            result=result.trim();
        return "i1 "+result;

    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "<"
     * f2 -> PrimaryExpression()
     */
    public String visit(CompareExpression n, String argu) throws Exception {
        String retStr="boolean";
//        String[] crop = argu.split("[.]");
        String arg1 = n.f0.accept(this, argu);
        arg1 = ridOf(arg1);

        n.f1.accept(this, argu);
        String arg2 = n.f2.accept(this, argu);
        arg2 = ridOf(arg2);

        emit += "\n; compare and set if "+arg1+" less than "+arg2;

        String result = getNewLabel(argu, null);

        arg1 = getIt(argu, arg1);
        arg2 = getIt(argu, arg2);

        emit += "\n\t"+result+" = icmp slt i32 "+arg1+", "+arg2;
        if(result!=null)
            result=result.trim();
        return "i1 "+result;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "+"
     * f2 -> PrimaryExpression()
     */
    public String visit(PlusExpression n, String argu) throws Exception {
        String retStr="int";
        String[] temp = argu.split("/");
        if(temp[0].equals("load"))
            return "";
//        String[] crop = argu.split("[.]");
        String arg1 = n.f0.accept(this, argu);
        arg1 = ridOf(arg1);

        n.f1.accept(this, argu);
        String arg2 = n.f2.accept(this, argu);
        arg2 = ridOf(arg2);

        arg1 = getIt(argu, arg1);
        arg2 = getIt(argu, arg2);

        emit += "\n; calculate sum of "+arg1+" and "+arg2;

        String result = getNewLabel(argu, null);

        System.out.println("calculate sum of "+arg1+" and "+arg2);

        emit += "\n\t"+result+" = add i32 "+arg1+", "+arg2;
        if(result!=null)
            result=result.trim();
        return "i32 "+result;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "-"
     * f2 -> PrimaryExpression()
     */
    public String visit(MinusExpression n, String argu) throws Exception {
        String retStr="int";
//        String[] crop = argu.split("[.]");
        String arg1 = n.f0.accept(this, argu);
        arg1 = ridOf(arg1);

        n.f1.accept(this, argu);
        String arg2 = n.f2.accept(this, argu);
        arg2 = ridOf(arg2);

        arg1 = getIt(argu, arg1);
        arg2 = getIt(argu, arg2);

        emit += "\n; subtract " + arg2 +" from "+arg1;

        String result = getNewLabel(argu, null);

        emit += "\n\t"+result+" = sub i32 "+arg1+", "+arg2;

        if(result!=null)
            result=result.trim();
        return "i32 "+result;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "*"
     * f2 -> PrimaryExpression()
     */
    public String visit(TimesExpression n, String argu) throws Exception {
        String retStr="int";

        String arg1 = n.f0.accept(this, argu);
        arg1 = ridOf(arg1);

        n.f1.accept(this, argu);
        String arg2 = n.f2.accept(this, argu);
        arg2 = ridOf(arg2);

        arg1 = getIt(argu, arg1);
        arg2 = getIt(argu, arg2);

        emit += "\n; multiply "+arg1+" by "+arg2;

        String result = getNewLabel(argu, null);

        emit += "\n\t"+result+" = mul i32 "+arg1+", "+arg2;

        if(result!=null)
            result=result.trim();
        return "i32 "+result;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "["
     * f2 -> PrimaryExpression()
     * f3 -> "]"
     */
    public String visit(ArrayLookup n, String argu) throws Exception {
        String retStr = "int";
        String[] temp = argu.split("/");
        int alloced = 0;
        String alloc = "";
        if (temp[0].equals("load"))
            return "";
        //to f0 tha prepei na einai Identifier enos int array
        //tha prepei na epistrefw ton typo tou identifier(xwris to array) afou koitaksw to symbolTable
        emit += "\n; load element from int array ";
        String name = n.f0.accept(this, argu);
        name = ridOf(name);

        String[] crop = argu.split("[.]");

        n.f1.accept(this, argu);
        String index = n.f2.accept(this, argu);
        index = ridOf(index);

        String reg0 = getNewLabel(argu, null);
        String reg1 = "";
        String reg2 = getNewLabel(argu, null);
        String reg3 = getNewLabel(argu, null);
        String reg4 = getNewLabel(argu, null);
        String reg5 = getNewLabel(argu, null);
        String reg6 = getNewLabel(argu, null);
        String reg7 = getNewLabel(argu, null);
        String oob_ok_Label = getNewExprLabel(argu, null);
        String oob_err_Label = getNewExprLabel(argu, null);

        if (Helper.isVariableRegister(index) == 0)
            if (Helper.isInt(index) == 0)
                index = getVariableRegister(argu, index);
        if (Helper.isMethodVariable(argu, name)){
            reg1 = getNewLabel(argu, null);
            emit += "\n\t" + reg0 + " = load i32*, i32** %" + name;
            emit += "\n\t" + reg1 + " = load i32, i32* " + reg0;
            emit += "\n\t" + reg2 + " = icmp sge i32 "+index+", 0";
            emit += "\n\t" + reg3 + " = icmp slt i32 "+index+", "+reg1;
            emit += "\n\t" + reg4 + " = and i1 "+reg2+", "+reg3;
        }else if(Helper.isField(argu, name)>=0){
            reg1 = getVariableRegister(argu, name);
            emit += "\n\t" + reg0 + " = load i32, i32 *" + reg1;
            emit += "\n\t" + reg4 + " = icmp ult i32 "+index+", "+reg0;
        }
        else {
            String[] tempr = name.split("/");
            if(tempr[0].equals("store"))
            {
                if(tempr[1].equals("int[]")) {
                    alloced=1;
                    alloc = tempr[2];
                    emit += "\n\t"+reg0+" = load i32, i32* "+tempr[2];
                    emit += "\n\t" + reg4 + " = icmp ult i32 "+index+", "+reg0;
                }
            }
            else if(Helper.isVariableRegister(name)==2){
                emit += "\n\t"+reg0+" = load i32, "+name;
                emit += "\n\t" + reg4 + " = icmp ult i32 "+index+", "+reg0;
            }
        }
        emit += "\n;yo is here " +name;
        emit += "\n\t" + "br i1 "+reg4+", label %"+oob_ok_Label+", label %"+oob_err_Label;

        emit += "\n" + oob_err_Label + ":";
        emit += "\n\t" + "call void @throw_oob()";
        emit += "\n\t" + "br label %"+oob_ok_Label;
        emit += "\n" + oob_ok_Label + ":";
        emit += "\n\t" + reg5 + " = add i32 1, "+index;
        if (Helper.isMethodVariable(argu, name))
            emit += "\n\t" + reg6 + " = getelementptr i32, i32* "+reg0+", i32 "+reg5;
        else if(Helper.isField(argu, name)>=0)
            emit += "\n\t" + reg6 + " = getelementptr i32, i32* "+reg1+", i32 "+reg5;
        else if(alloced>0)
            emit += "\n\t" + reg6 + " = getelementptr i32, i32* "+alloc+", i32 "+reg5;
        else if(Helper.isVariableRegister(name)==2)
            emit += "\n\t" + reg6 + " = getelementptr i32, "+name+", i32 "+reg5;
        emit += "\n\t" + reg7 + " = load i32, i32* "+reg6;

        n.f3.accept(this, argu);
        retStr = reg7;
        if(retStr!=null)
            retStr=retStr.trim();
        return "i32 " +retStr;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> "length"
     */
    public String visit(ArrayLength n, String argu) throws Exception {
        String retStr="int";
        String array = n.f0.accept(this, argu);
        array = ridOf(array);

        String[] crop = argu.split("[.]");
        String reg0 = null;
        String reg1 = null;

        emit += "\n; get length of array: "+array;
        if (Helper.isMethodVariable(argu, array)){
            reg0 = getNewLabel(argu, null);
            reg1 = getNewLabel(argu, null);
            emit += "\n\t" + reg0 + " = load i32*, i32** %" + array;
            emit += "\n\t" + reg1 + " = load i32, i32* " + reg0;
        }else if(Helper.isField(argu, array)>=0){
            reg0 = getVariableRegister(argu, array);
            reg1 = getNewLabel(argu, null);
            emit += "\n\t" + reg1 + " = load i32, i32 *" + reg0;
        }
        else {
            String[] tempr = array.split("/");
            reg1 = getNewLabel(argu, null);
            if(tempr[0].equals("store"))
            {
                if(tempr[1].equals("int[]")) {
                    emit += "\n\t"+reg1+" = load i32, i32* "+tempr[2];
                }
            }
            else if(Helper.isVariableRegister(array)==2){
                emit += "\n\t"+reg1+" = load i32, "+array;
            }
        }
        retStr = reg1;

        if(retStr!=null)
            retStr=retStr.trim();
        return "i32 "+retStr;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( ExpressionList() )?
     * f5 -> ")"
     */
    public String visit(MessageSend n, String argu) throws Exception {
        String retStr=null;
        int isNew = 0;
        int isCall = 0;
        int argisField = 0;
        emit += "\n; message send";
        String arg1 = n.f0.accept(this, argu);
        n.f1.accept(this, argu);

        String method = n.f2.accept(this, argu);
        n.f3.accept(this, argu);

        String argList = n.f4.present() ? n.f4.accept(this, argu) : "";
        n.f5.accept(this, argu);

        emit += "\n; "+arg1 +"."+method+"("+argList+")";
        String[] temp = arg1.split("/");
//        System.out.println("crop = "+crop[0]+" "+argu);
        String storednew = getNewLabel(argu, null);
        String newClass = "";
        if(temp[0].equals("store"))
        {
            isNew = 1;
            //emit += "\n; store value: "+temp[2]+" into variable: "+storednew;
            if(temp[1].equals("object")) {
                arg1 = temp[2];
                if(temp.length>3)
                    newClass = temp[3];
                //emit += "\n\tstore i8* " + temp[2] + ", i8** " + storednew;
                //return storednew;
            }
            if(temp[1].equals("int[]")) {
                arg1 = temp[2];
                //emit += "\n\tstore i32* " + temp[2] + ", i32** " + storednew;
                //return storednew;
            }
        }
        else if(temp[temp.length-1].startsWith("class:")){
            isCall=1;
            newClass = temp[temp.length-1].substring("class:".length());
            arg1 = ridOf(arg1);
            String[] tempora = arg1.split("\\s");
            arg1 = tempora[1];
//            System.out.println(newClass);
//            System.exit(1);
        }

//            arg1 = getVariableRegister(argu, arg1);
//        System.out.println("new");
        System.out.println("firstofal!!!!!!!!!!!"+argu+" "+arg1+"."+method+"("+argList+")");
        String[] crop = argu.split("[.]");
        String fieldarg = "";
        if(Helper.isField(crop[0] , arg1)>=0){
            argisField=1;
            String freg = getNewLabel(argu, null);
            fieldarg = getNewLabel(argu, null);
            emit += "\n\t"+freg+" = getelementptr i8, i8* %this, i32 "+(Helper.isField(crop[0] , arg1)+8);
            emit += "\n\t"+fieldarg+" = bitcast i8* "+freg+" to i8**";
        }
        String classmethod = null;
        if(crop.length>1)
            classmethod = crop[1];
        emit += "\n; function call ";

        String[] arguments = argList.split(",");
        String[] loadedArguments = new String[arguments.length];
        for(int i=0; i<arguments.length; i++)
        {
            arguments[i] = arguments[i].trim();

            if(arguments[i].equals(""))
                continue;
            emit += "\n; arg"+i+":"+" "+arguments[i];
            System.out.println("!!!!!!!!!!!!!!!\n; arg"+i+":"+" "+arguments[i]);
            String[] tempstore = arguments[i].split("/");
            if(tempstore[0].equals("store"))
            {
//                System.out.println(";"+arg1);
//                emit += "\n; store value: "+temp[2]+" into variable: "+storednew;
                if(tempstore[1].equals("object")) {
//                    arg1 = tempstore[2];
//                    if(tempstore.length>3)
//                        newClass = tempstore[3];
                    loadedArguments[i] = tempstore[2];
                    //emit += "\n\tstore i8* " + temp[2] + ", i8** " + storednew;
                    //return storednew;
                }
                if(tempstore[1].equals("int[]")) {
                    loadedArguments[i] = tempstore[2];
//                    arg1 = tempstore[2];
                    //emit += "\n\tstore i32* " + temp[2] + ", i32** " + storednew;
                    //return storednew;
                }
            }
            else if(Helper.isVariableRegister(arguments[i])==0)
            {
                if(arguments[i].trim().equals("this")){
                    loadedArguments[i] = "%this";
                }
                else if (Helper.isInt(arguments[i].trim()) == 2) {
                    String[] temporo = arguments[i].split("\\s");
                    loadedArguments[i] = temporo[temporo.length-1].trim();
                    emit += "\n;2; arg"+i+":"+" "+loadedArguments[i];
                }
                else if (Helper.isInt(arguments[i]) == 0 && labelExists(argu, arguments[i])) {
                    loadedArguments[i] = getVariableRegister(argu, arguments[i]);
                    emit += "\n;0; arg"+i+":"+" "+loadedArguments[i];
                }
                else if(Helper.isInt(arguments[i])==0 && !labelExists(argu, arguments[i]))
                {
                    loadedArguments[i] = Helper.doLoad(argu, arguments[i]);
                    emit += "\n;1; arg"+i+":"+" "+loadedArguments[i];
                }
                else {
                    loadedArguments[i] = arguments[i];
                    emit += "\n;3; arg"+i+":"+" "+loadedArguments[i];
                }
            }
            else if(Helper.isVariableRegister(arguments[i])==1) {
                loadedArguments[i] = arguments[i];
                emit += "\n;3; arg" + i + ":" + " " + loadedArguments[i];
            }
            else if(Helper.isVariableRegister(arguments[i])==2) {
                String[] tempori = arguments[i].trim().split("\\s");
                loadedArguments[i] = tempori[1];
                emit += "\n;3; arg" + i + ":" + " " + loadedArguments[i];
            }
            emit += "\n; loadedArg"+i+":"+" "+loadedArguments[i];
        }

        String load = getNewLabel(argu, null);  //arg1
        String bitcast = getNewLabel(argu, null); //arg2
        String vtable_ptr = getNewLabel(argu, null); //argList
        String getelementptr = getNewLabel(argu, null);
        String func_pointer = getNewLabel(argu, null);
        String cast = getNewLabel(argu, null);
        String call = getNewLabel(argu, null);


        if(!arg1.equals("this") && isNew==0 && isCall==0){
            if(argisField==1)
                emit += "\n\t" + load + " = load i8*, i8** " + fieldarg;
            else
                emit += "\n\t" + load + " = load i8*, i8** %" + arg1;
            emit += "\n\t" + bitcast + " = bitcast i8* " + load + " to i8***";
            emit += "\n\t" + vtable_ptr + " = load i8**, i8*** " + bitcast;
            emit += "\n; is "+method+" field of "+Helper.getTypeOfVariable(crop[0], classmethod, arg1)+ " "+Helper.isField(Helper.getTypeOfVariable(crop[0], classmethod, arg1), method);
            emit += "\n\t" + getelementptr + " = getelementptr i8*, i8** " + vtable_ptr + ", i32 " + (Helper.isField(Helper.getTypeOfVariable(crop[0], classmethod, arg1), method)/8);
        }
        else if(arg1.equals("this")){
            emit += "\n\t" + bitcast + " = bitcast i8* %this to i8***";
            emit += "\n\t" + load + " = load i8**, i8*** "+bitcast;
            emit += "\n; is "+method+" field of "+Helper.getTypeOfVariable(crop[0], classmethod, arg1) + " "+Helper.isField(Helper.getTypeOfVariable(crop[0], classmethod, arg1), method);
            emit += "\n\t" + getelementptr + " = getelementptr i8*, i8** " + load + ", i32 " + (Helper.isField(Helper.getTypeOfVariable(crop[0], classmethod, arg1), method)/8);
        }
        else if(isNew==1 || isCall==1){
            emit += "\n\t" + bitcast + " = bitcast i8* " + arg1 + " to i8***";
            emit += "\n\t" + vtable_ptr + " = load i8**, i8*** " + bitcast;
            emit += "\n\t" + getelementptr + " = getelementptr i8*, i8** " + vtable_ptr + ", i32 " + (Helper.isField(newClass, method)/8);
        }
        emit += "\n\t" + func_pointer + " = load i8*, i8** " + getelementptr;
        System.out.println("argu is |" + crop[0] + "||" + arg1);
        if(isNew==1 || isCall==1)
            emit += "\n\t" + cast + " = bitcast i8* " + func_pointer + " to " + Helper.getVtableMethodType(newClass, method);
        else
            emit += "\n\t" + cast + " = bitcast i8* " + func_pointer + " to " + Helper.getVtableMethodType(Helper.getTypeOfVariable(crop[0], classmethod, arg1), method);
        if(isNew==1 || isCall==1)
            emit += "\n\t" + call + " = call " + Helper.getVtableMethodReturnType(newClass, method).trim() + " " + cast + "(i8* " + arg1;
        else if(!arg1.equals("this") && isNew==0)
            emit += "\n\t" + call + " = call " + Helper.getVtableMethodReturnType(Helper.getTypeOfVariable(crop[0], classmethod, arg1), method).trim() + " " + cast + "(i8* " + load;
        else if(arg1.equals("this"))
            emit += "\n\t" + call + " = call " + Helper.getVtableMethodReturnType(Helper.getTypeOfVariable(crop[0], classmethod, arg1), method).trim() + " " + cast + "(i8* " + "%this";

        int argCounter = 0;
        String myClass = "";
        if(isNew==1 || isCall==1)
            myClass = newClass;
        else
            myClass = Helper.getTypeOfVariable(crop[0], classmethod, arg1);
        if(DataVisitor.parameters.containsKey(myClass+"."+method))
        {
            ArrayList<String> myParameters = new ArrayList<>(DataVisitor.parameters.get(myClass+"."+method).values());
            for (String myParameter : myParameters)
            {
                String[] tempVar = myParameter.split("\\s");
                if(DataVisitor.simpleClasses.contains(tempVar[0]))
                    emit += ",i8* "+loadedArguments[argCounter++];
                else if(tempVar[0].equals("int"))
                    emit += ",i32 "+loadedArguments[argCounter++];
                else if(tempVar[0].equals("int[]"))
                    emit += ",i32* "+loadedArguments[argCounter++];
                else if(tempVar[0].equals("boolean"))
                    emit += ",i1 "+loadedArguments[argCounter++];
            }
        }
        else
        {
            //find ancestors
            ArrayList<String> ancestors = Helper.findAncestors(myClass);
            assert ancestors != null;
            for (String ancestor : ancestors)
            {
                if (DataVisitor.parameters.containsKey(ancestor + "." + method))
                {
                    ArrayList<String> myParameters = new ArrayList<>(DataVisitor.parameters.get(ancestor + "." + method).values());
                    for (String myParameter : myParameters)
                    {
                        String[] tempVar = myParameter.split("\\s");
                        if (DataVisitor.simpleClasses.contains(tempVar[0]))
                            emit += ",i8* "+loadedArguments[argCounter++];
                        if (tempVar[0].equals("int"))
                            emit += ",i32 "+loadedArguments[argCounter++];
                        if (tempVar[0].equals("int[]"))
                            emit += ",i32* "+loadedArguments[argCounter++];
                        if (tempVar[0].equals("boolean"))
                            emit += ",i1 "+loadedArguments[argCounter++];
                    }
                }
            }
        }
        emit += ")";
        if(isNew==0 && isCall==0)
            return Helper.getVtableMethodReturnType(Helper.getTypeOfVariable(crop[0], classmethod, arg1), method).trim() + " " +call.trim()+" /class:"+Helper.getTypeOfVariable(crop[0], classmethod, arg1).trim();
        else
            return Helper.getVtableMethodReturnType(newClass, method).trim() + " " +call.trim() +" /class:"+newClass.trim();
    }


    //    ExpressionList 	::= 	Expression() ExpressionTail()
    /**
     * f0 -> 	Expression()
     * f1 -> 	ExpressionTail()
     **/
    @Override
    public String visit(ExpressionList n, String argu) throws Exception{
//        System.out.println();
//        super.visit(n, argu);
//        System.out.println();
        String ret = n.f0.accept(this, argu);
        if (n.f1 != null) {
            ret += n.f1.accept(this, argu);
        }
        return ret;
    }

//    ExpressionTail() 	::= 	"," Expression
    /**
     * f0 -> ( ExpressionTerm() )*
     */
    @Override
    public String visit(ExpressionTail n, String argu) throws Exception{
        String ret = "";
        for ( Node node: n.f0.nodes) {
            ret += ", " + node.accept(this, argu);
        }
        return ret;
    }

//    ExpressionTerm() 	::= 	"," Expression
    /**
     * f0 -> ","
     * f1 -> Expression()
     */
    @Override
    public String visit(ExpressionTerm n, String argu) throws Exception{
        return n.f1.accept(this, argu);
    }

    /**
     * PrimaryExpression -> IntegerLiteral() OK epistrefei typeInt
     *                   | TrueLiteral() OK epistrefei typeBoolean
     *                   | FalseLiteral() OK epistrefei typeBoolean
     *                   | Identifier() OK epistrefei id
     *                   | ThisExpression() OK epistrefei this
     *                   | ArrayAllocationExpression() OK θα πρεπει ο identifier na elegxei an yparxei tetoio class
     *                                      kai ua prepei na elgxetai apo to anwtero epipedo an ginetai swsto assignment
     *                   | AllocationExpression() OK θα πρεπει ο identifier na elegxei an yparxei tetoio class
     *                                  kai ua prepei na elgxetai apo to anwtero epipedo an ginetai swsto assignment
     *                   | NotExpression() OK epistrefei typeBoolean
     *                   | BracketExpression() OK epistrefei typeBoolean na elegxei an einai boolean mesa
     */
    public String visit(PrimaryExpression n, String argu) throws Exception {
        n.f0.accept(this, "load/"+argu);
        String retStr = n.f0.accept(this, argu);
        String arg;
        String[] crop = argu.split("[.]");
        if(retStr==null){
            System.out.println("PrimaryExpression received null");
            throw new ParseException("ERROR");
//            System.exit(1);
        }
//        if(!retStr.equals("int")&&!retStr.equals("boolean")) {
//            arg = Helper.getTypeOfVariable(crop[0], argu, retStr);
//            if(!arg.equals(""))
//                return arg;
//        }
//        System.out.println("PrimaryExpression: "+retStr);
        if(retStr!=null)
            retStr=retStr.trim();
        return retStr;
    }

    /**
     * f0 -> <INTEGER_LITERAL>
     */
    public String visit(IntegerLiteral n, String argu) throws Exception {
//        if(Helper.isInt(n.f0.toString())==1)
//            return "int";
//        else{
//            System.out.println("ERROR: invalid integer: "+ n.f0.toString());
//            throw new ParseException("ERROR");
//        }
        return n.f0.toString();
    }

    /**
     * f0 -> "true"
     */
    public String visit(TrueLiteral n, String argu) throws Exception {
        String[] temp = argu.split("/");
        if(temp[0].equals("load"))
            return "";
//        return "boolean";
        return "i1 1";

    }

    /**
     * f0 -> "false"
     */
    public String visit(FalseLiteral n, String argu) throws Exception {
        String[] temp = argu.split("/");
        if(temp[0].equals("load"))
            return "";
//        return "boolean";
        return "i1 0";
    }

    /**
     * f0 -> <IDENTIFIER>
     */
    public String visit(Identifier n, String argu) throws Exception {
        String arg;
        String retStr = n.f0.toString();
        String ret = retStr;
//        System.out.println("IDENTIFIER "+retStr);
        if(DataVisitor.simpleClasses.contains(retStr))
            return retStr;
        if(retStr.equals("this"))
            return retStr;

        //care einai to xrhsimopoiumeno argu
        //crop1[0] einai to classname
        //retStr einai to identifier

        if(argu!=null)
        {
            String[] crop = argu.split("/");
            if(crop.length>2 && crop[0].equals("load") && crop[1].equals("load"))
                return retStr;
            String care = "";
            for(int k=0; k<crop.length;k++)
                if(!crop[k].equals("load")) {
                    care = crop[k];
                    break;
                }
            if(care.equals("this"))
                return retStr;
            if (crop[0].equals("load") && !labelExists(care, retStr))
            {
                emit += "\n; load "+ret;
                String[] crop1 = crop[1].split("[.]");
//                System.out.println(argu + " "+crop1[0]+" "+care+" " +retStr);
                arg = Helper.getTypeOfVariable(crop1[0], care, retStr);
                if (arg.equals("")) {
                    System.out.println("Cannot find type of "+retStr);
                    if (DataVisitor.simpleClasses.contains(retStr))
                        return retStr;
//                    if(retStr.equals(DataVisitor.mainClass))
//                        return retStr;
                    throw new ParseException("internal ERROR: cannot discover type of "+retStr);
                }

                if(Helper.isMethodVariable(care, retStr)) {
//                    emit += "\n;"+ retStr+" is method var";
                    String label = getNewLabel(argu, retStr);

                    if (DataVisitor.simpleClasses.contains(arg)) {
                        emit += "\n\t" + label + " = load i8*, i8** %" + retStr;
                    } else if (arg.equals("int")) {
                        emit += "\n\t" + label + " = load i32, i32* %" + retStr;
                    } else if (arg.equals("int[]")) {
                        emit += "\n\t" + label + " = load i32*, i32** %" + retStr;
                    } else if (arg.equals("boolean")) {
                        emit += "\n\t" + label + " = load i1, i1* %" + retStr;
                    }
                }
                else if(Helper.isField(care, retStr)>=0){
                    String getelementptr = getNewLabel(care, null);
                    String bitcast = getNewLabel(care, null);
                    String load = getNewLabel(care, retStr);
                    emit += "\n\t" + getelementptr + " = getelementptr i8, i8* %this, i32 " + (Helper.isField(care, retStr)+8);
                    emit += "\n\t" + bitcast + " = bitcast i8* "+getelementptr+" to ";
                    String type = Helper.getTypeOfVariable(crop1[0], null, retStr);
                    String[] tempVar = type.split("\\s");
                    if(DataVisitor.simpleClasses.contains(tempVar[0])) {
                        emit += "i8** ";
                        retStr = "i8** "+bitcast;
                    }if(tempVar[0].equals("int")) {
                        emit += "i32* ";
                        retStr = "i32* "+bitcast;
                    }if(tempVar[0].equals("int[]")) {
                        emit += "i32** ";
                        retStr = "i32** "+bitcast;
                    }if(tempVar[0].equals("boolean")) {
                        emit += "i1* ";
                        retStr = "i1* "+bitcast;
                    }

                    emit += "\n\t" + load + " = load ";
                    if(DataVisitor.simpleClasses.contains(tempVar[0])) {
                        emit += "i8*, ";
                    }else if(tempVar[0].equals("int")) {
                        emit += "i32, ";
                    }else if(tempVar[0].equals("int[]")) {
                        emit += "i32*, ";
                    }else if(tempVar[0].equals("boolean")) {
                        emit += "i1, ";
                    }
                    emit += retStr;
                }
            }
        }
        retStr = ret;
        if(retStr!=null)
            retStr=retStr.trim();
        return retStr;
    }

    /**
     * f0 -> "this"
     */
    public String visit(ThisExpression n, String argu) throws Exception {
        String[] temp = argu.split("/");
        if(temp[0].equals("load"))
            return "";
        emit += "\n; this";
        String label = getNewLabel(argu, "this");
        emit += "\n\t" + label+ " = bitcast i8* %this to i8***";
        return n.f0.toString();
    }

    /**
     * f0 -> "new"
     * f1 -> "int"
     * f2 -> "["
     * f3 -> Expression()
     * f4 -> "]"
     */
    public String visit(ArrayAllocationExpression n, String argu) throws Exception {
        String retStr="new int []";
        String[] temp = argu.split("/");
        if(temp[0].equals("load"))
            return "";
        String[] crop = argu.split("[.]");
        String arg = n.f3.accept(this, argu);
        emit += "\n; allocate new int array";

        String nsz_err_Label = getNewExprLabel(argu, null);
        String nsz_ok_Label = getNewExprLabel(argu, null);
        String reg0 = getNewLabel(argu, null);
        String reg1 = getNewLabel(argu, null);
        String reg2 = getNewLabel(argu, null);
        String reg3 = getNewLabel(argu, null);
        String result = getNewLabel(argu, null);
        String size = "";
        size = getIt(argu, arg);
        emit += "\n\t"+reg0+" = add i32 1, "+size;
        emit += "\n\t"+reg1+" = icmp sge i32 "+reg0+", 1";
        emit += "\n\t"+"br i1 "+reg1+", label %"+nsz_ok_Label+", label %"+nsz_err_Label;
        emit += "\n"+nsz_err_Label+":";
        emit += "\n\t"+"call void @throw_nsz()";
        emit += "\n\t"+"br label %"+nsz_ok_Label;
        emit += "\n"+nsz_ok_Label+":";
        emit += "\n\t"+reg2+" = call i8* @calloc(i32 "+reg0+", i32 4)";
        emit += "\n\t"+reg3+" = bitcast i8* "+reg2+" to i32*";
        emit += "\n\t"+"store i32 "+size+", i32* "+reg3;
        return "store/int[]/"+reg3.trim();
    }

    /**
     * f0 -> "new"
     * f1 -> Identifier()
     * f2 -> "("
     * f3 -> ")"
     */
    public String visit(AllocationExpression n, String argu) throws Exception {
//        n.f0.accept(this, argu);
        String scope = argu;
        String[] a = argu.split("/");
        if(a[0].equals("load"))
            return null;
        String name = n.f1.accept(this, argu);
        String[] tempc = argu.split("[.]");
        if(tempc.length>1 && !tempc[1].isEmpty())
            argu = tempc[0];
        String[] classname = argu.split("\\s");
        if(classname.length>1 && classname[1].equals("extends"))
            argu = classname[0];
        emit += "\n; allocate new "+name+" object ";
        String retStr = "new " + name + " ()";
        String calloc = "";
        String bitcast;
        String getelementptr;
        if (DataVisitor.simpleClasses.contains(name)) {
            calloc = getNewLabel(scope, null);
            bitcast = getNewLabel(scope, null);
            getelementptr = getNewLabel(scope, null);
            emit += "\n\t" + calloc + " = call i8* @calloc(i32 1, i32 " + (Helper.getSizeOfObject(name)+8) +")";
            emit += "\n\t" + bitcast + " = bitcast i8* " + calloc + " to i8***";
            emit += "\n\t" + getelementptr + " = getelementptr " + Helper.vtabletype.get(name) + ", " + Helper.vtabletype.get(name)+"* "+Helper.getVtable(name) + ", i32 0, i32 0";
            emit += "\n\tstore i8** "+ getelementptr +", i8*** "+bitcast;
        }
        if(name!=null)
            name=name.trim();
        return "store/object/"+calloc.trim()+"/"+name;
    }

    /**
     * f0 -> "!"
     * f1 -> PrimaryExpression()
     */
    public String visit(NotExpression n, String argu) throws Exception {
        String[] tempo = argu.split("/");
        if(tempo[0].equals("load"))
            return "";
        String retStr="boolean";
        emit += "\n; not expression";
        n.f0.accept(this, argu);
        String[] crop = argu.split("[.]");
        String arg1 = n.f1.accept(this, argu);
        arg1 = ridOf(arg1);
        arg1=getIt(argu,arg1);

        String nsz_els_Label = getNewExprLabel(argu, null);
        String nsz_if_Label = getNewExprLabel(argu, null);
        String nsz_endif_Label = getNewExprLabel(argu, null);
        String result = getNewLabel(argu, null);

        emit += "\n\t; not expression";
        emit += "\n\t "+"br i1 "+arg1+", label %"+nsz_if_Label+", label %"+nsz_els_Label;
        emit += "\n"+nsz_els_Label+":";
        emit += "\n\t"+"br label %"+nsz_endif_Label;
        emit += "\n"+nsz_if_Label+":";
        emit += "\n\t"+"br label %"+nsz_endif_Label;
        emit += "\n"+nsz_endif_Label+":";
        emit += "\n\t"+result+" = phi i1  [ 0, %"+nsz_if_Label+" ], [ "+1+", %"+nsz_els_Label+" ]";
        emit += "\n\t"+"; end of not expression";

        retStr = result;
        if(retStr!=null)
            retStr=retStr.trim();
        return retStr;
    }

    /**
     * f0 -> "("
     * f1 -> Expression()
     * f2 -> ")"
     */
    public String visit(BracketExpression n, String argu) throws Exception {
        String[] temp = argu.split("/");
        if(temp[0].equals("load"))
            return "";
//        emit += "\n; expression in brackets";
        String retStr=null;
        n.f0.accept(this, argu);
        retStr = n.f1.accept(this, argu);
//        System.exit(1);
        n.f2.accept(this, argu);
        if(retStr!=null)
            retStr=retStr.trim();
        return retStr;
    }
}

