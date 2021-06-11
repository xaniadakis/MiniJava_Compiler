import java.util.*;
import java.util.concurrent.TimeUnit;

public class Helper {

    public static Map<String, Map<Integer,String>> methodOffsets = null;
    public static Map<String, Map<Integer,String>> fieldOffsets = null;
    public static Map<String, String> vtabletype = null;
    public static Map<String, Map<String,String>> vtablemethodtype = null;

    public static <Integer, String> int getKey(Map<Integer, String> map, String value)
    {
        if(map!=null){
            for (Map.Entry<Integer, String> entry : map.entrySet())
            {
                String[] newval = (String[]) entry.getValue().toString().split("\\s");
                if (newval[1].equals(value)) {
                    return (int) entry.getKey();
                }
            }}
        return -1;
    }

    public static String doLoad(String argu, String variable) throws Exception
    {
        String reg = "";
        System.out.println("loading "+variable);
        variable = variable.trim();
        argu = "load/"+argu;
        String arg;
        String retStr = variable;
        String ret = retStr;
//        Generator.emit += "\n; IDENTIFIER "+argu +" "+variable;
        if(DataVisitor.simpleClasses.contains(retStr))
            return retStr;
        if(retStr.equals("this"))
            return retStr;
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
            if (crop[0].equals("load"))
            {
                Generator.emit += "\n; load "+ret;
                String[] crop1 = crop[1].split("[.]");
                arg = Helper.getTypeOfVariable(crop1[0].trim(), care.trim(), retStr.trim());
//                Generator.emit += "YOOO! "+argu + " "+crop1[0]+" "+care+" " +retStr+"  arg="+arg;
//                System.out.println("out");
                if (arg.equals("")) {
                    System.out.println("Cannot find type of "+retStr);
                    if (DataVisitor.simpleClasses.contains(retStr))
                        return retStr;
                    //throw new ParseException("internal ERROR");
                }

                if(Helper.isMethodVariable(care, retStr)) {
//                    Generator.emit += "\n;"+ retStr+" is method var";
                    String label = Generator.getNewLabel(argu, retStr);
                    if (DataVisitor.simpleClasses.contains(arg)) {
                        Generator.emit += "\n\t" + label + " = load i8*, i8** %" + retStr;
                    } else if (arg.equals("int")) {
                        Generator.emit += "\n\t" + label + " = load i32, i32* %" + retStr;
                    } else if (arg.equals("int[]")) {
                        Generator.emit += "\n\t" + label + " = load i32*, i32** %" + retStr;
                    } else if (arg.equals("boolean")) {
                        Generator.emit += "\n\t" + label + " = load i1, i1* %" + retStr;
                    }
                    reg=label;
                }
                else if(Helper.isField(crop1[0], retStr)>=0){
//                    Generator.emit += "\n;"+ retStr+" isField";
                    String getelementptr = Generator.getNewLabel(care, null);
                    String bitcast = Generator.getNewLabel(care, null);
                    String load = Generator.getNewLabel(care, retStr);
                    Generator.emit += "\n\t" + getelementptr + " = getelementptr i8, i8* %this, i32 " + (Helper.isField(care, retStr)+8);
                    Generator.emit += "\n\t" + bitcast + " = bitcast i8* "+getelementptr+" to ";
                    String type = Helper.getTypeOfVariable(crop1[0], null, retStr);
                    String[] tempVar = type.split("\\s");
                    if(DataVisitor.simpleClasses.contains(tempVar[0])) {
                        Generator.emit += "i8** ";
                        retStr = "i8** "+bitcast;
                    }if(tempVar[0].equals("int")) {
                        Generator.emit += "i32* ";
                        retStr = "i32* "+bitcast;
                    }if(tempVar[0].equals("int[]")) {
                        Generator.emit += "i32** ";
                        retStr = "i32** "+bitcast;
                    }if(tempVar[0].equals("boolean")) {
                        Generator.emit += "i1* ";
                        retStr = "i1* "+bitcast;
                    }
                    Generator.emit += "\n\t" + load + " = load ";
                    if(DataVisitor.simpleClasses.contains(tempVar[0])) {
                        Generator.emit += "i8*, ";
                    }else if(tempVar[0].equals("int")) {
                        Generator.emit += "i32, ";
                    }else if(tempVar[0].equals("int[]")) {
                        Generator.emit += "i32*, ";
                    }else if(tempVar[0].equals("boolean")) {
                        Generator.emit += "i1, ";
                    }
                    Generator.emit += retStr;
                    reg = load;
                }
            }
        }
        retStr = reg;
        return retStr;
    }

    public static String VTable(String myClass)
    {
        String cl = null;
        String realClass = null;
        String parentClass = null;
        List<String> ancestors = new ArrayList<String>();
        List<String> realAncestors = new ArrayList<String>();
        int found = 0;
        int counter = 0;
        cl = myClass;
        if(DataVisitor.classes.contains(myClass))
            realClass = myClass;
        else
        {
            for(int i=0;i<DataVisitor.classes.size();i++)
            {
                String temp[] = DataVisitor.classes.get(i).split("\\s");
                if(temp[0].equals(myClass))
                {
                    realClass = DataVisitor.classes.get(i);
                    parentClass = temp[2];
                    ancestors.add(temp[2]);
//                    System.out.println("parentclass of "+myClass+"="+parentClass);
                    if(DataVisitor.classes.contains(parentClass))
                        realAncestors.add(temp[2]);
                    else {
                        while (true) {
                            for (int j = 0; j < DataVisitor.classes.size(); j++) {
                                temp = DataVisitor.classes.get(j).split("\\s");
                                if (temp[0].equals(parentClass)) {
                                    parentClass = temp[2];
                                    ancestors.add(temp[2]);
                                    realAncestors.add(DataVisitor.classes.get(j));
                                    break;
                                }
                            }
                            if (DataVisitor.classes.contains(parentClass)) {
                                realAncestors.add(temp[2]);
                                break;
                            }
                        }
                    }
                    break;
                }
            }
        }
        if(cl == null) {
            System.out.println("ERROR: internal error");
            System.exit(1);
        }

//        System.out.println("ancestors of "+myClass+realAncestors);
        //for(int l=0;l<ancestors.size();l++)
        int counter1=1;
//        if(ancestors.size()>0)
//            while(true) {
////                System.out.println("last ancestor of " + myClass + " " + ancestors.get(ancestors.size() - counter1++));
//                    if(ancestors.size() - counter1<0)
//                        break;
//            }

        String retStr = "";
        //if the class does not extend no class
        if(realClass.equals(myClass))
        {
            Map<String,String> innervtablemethodtype = new TreeMap<>();
            vtablemethodtype.put(myClass,innervtablemethodtype);
            for(int i = 0; i < DataVisitor.methods.size(); i++) {
                String method = "";
                String temp[] = DataVisitor.methods.get(i).split("/");
                String temp2[] = temp[0].split("[.]");
                String temp3[] = temp[0].split("\\s");
                if (temp3[0].equals(cl) || temp2[0].equals(cl))
                {
                    counter++;
                    if (found == 0) {
                        //                    retStr += "\n";
                        found++;
                    } else
                        retStr += ",";
                    retStr += "\n\t" + "i8* bitcast (";
                    if (temp[1].equals("int")) {
                        retStr += "i32 ";
                        method += "i32 ";
                    }
                    else if (temp[1].equals("int[]")){
                        retStr += "i32* ";
                        method += "i32* ";
                    }
                    else if (temp[1].equals("boolean")){
                        retStr += "i1 ";
                        method += "i1 ";
                    }
                    else{
                        retStr += "i8* ";
                        method += "i8* ";
                    }
                    retStr += "(i8*";
                    method += "(i8*";
                    if (DataVisitor.parameters.containsKey(temp[0])) {
                        String temp4[] = DataVisitor.parameters.get(temp[0]).values().toArray(new String[0]);
                        for (int j = 0; j < temp4.length; j++) {
                            String temp5[] = temp4[j].split("\\s");//type
                            if (temp5[0].equals("int")) {
                                retStr += ",i32";
                                method += ",i32";
                            }
                            else if (temp5[0].equals("int[]")) {
                                retStr += ",i32*";
                                method += ",i32*";
                            }
                            else if (temp5[0].equals("boolean")) {
                                retStr += ",i1";
                                method += ",i1";
                            }
                            else {
                                retStr += ",i8*";
                                method += ",i8*";
                            }
                        }
                    }
                    retStr += ")* @" + cl + "." + temp2[1] + " to i8*)";
                    method += ")*";
                    vtablemethodtype.get(myClass).put(temp2[1], method);
                }
            }
        }
        //if the class does extend
        else
        {
            Map<String,String> innervtablemethodtype = new TreeMap<>();
            vtablemethodtype.put(myClass,innervtablemethodtype);
//            System.out.println("we got an extendor man");
//            for (int m=realAncestors.size()-1; m>=0; m--)
//            {
            List<String> printed = new ArrayList<String>();
            for(int i=0; i<DataVisitor.methods.size(); i++)
            {
                String method = "";
                String temp[] = DataVisitor.methods.get(i).split("/");
                String temp2[] = temp[0].split("[.]");
                String temp3[] = temp[0].split("\\s");
                //first print methods inherited from parents
                //and if a method has been overridden print the overridden
                for (int m=realAncestors.size()-1; m>=0; m--)
                {
                    if(!printed.contains(temp2[1]))
                        if(temp3[0].equals(realAncestors.get(m)) || temp2[0].equals(realAncestors.get(m)))
                        {
                            counter++;
                            if (found == 0) {
                                //                    retStr += "\n";
                                found++;
                            } else
                                retStr += ",";
                            retStr += "\n\t" + "i8* bitcast (";
                            if (temp[1].equals("int")){
                                retStr += "i32 ";
                                method += "i32 ";
                            }
                            else if (temp[1].equals("int[]")){
                                retStr += "i32* ";
                                method += "i32* ";
                            }
                            else if (temp[1].equals("boolean")) {
                                retStr += "i1 ";
                                method += "i1 ";
                            }else {
                                retStr += "i8* ";
                                method += "i8* ";
                            }
                            retStr += "(i8*";
                            method += "(i8*";
                            if (DataVisitor.parameters.containsKey(temp[0])) {
                                String temp4[] = DataVisitor.parameters.get(temp[0]).values().toArray(new String[0]);
                                for (int j = 0; j < temp4.length; j++) {
                                    String temp5[] = temp4[j].split("\\s");//type
                                    if (temp5[0].equals("int")) {
                                        retStr += ",i32";
                                        method += ",i32";
                                    }else if (temp5[0].equals("int[]")) {
                                        retStr += ",i32*";
                                        method += ",i32*";
                                    }else if (temp5[0].equals("boolean")) {
                                        retStr += ",i1";
                                        method += ",i1";
                                    }else {
                                        retStr += ",i8*";
                                        method += ",i8*";
                                    }
                                }
                            }
                            String tmp = cl;
                            counter1 = 1;
                            while(true) {
    //                            System.out.println(cl+" yooo "+ ancestors+ " yooo " +realAncestors.get(realAncestors.size()-counter1)+"."+temp2[1]+"/"+temp[1]+"/"+temp[2]);
                                if(DataVisitor.methods.contains(realAncestors.get(realAncestors.size()-counter1)+"."+temp2[1]+"/"+temp[1]+"/"+temp[2]))
                                    tmp = ancestors.get(ancestors.size()-counter1);
                                counter1++;
                                if(realAncestors.size() - counter1<0)
                                    break;
                            }
    //                        System.out.println(cl+" yooo "+ ancestors+ " yooo " +myClass+"."+temp2[1]+"/"+temp[1]+"/"+temp[2]);
                            if(DataVisitor.methods.contains(realClass+"."+temp2[1]+"/"+temp[1]+"/"+temp[2]))
                                tmp = myClass;
                            retStr += ")* @" + tmp + "." + temp2[1] + " to i8*)";
                            method += ")*";
                            vtablemethodtype.get(myClass).put(temp2[1], method);
                            printed.add(temp2[1]);
                        }
                }
            }
            for(int i=0; i<DataVisitor.methods.size(); i++)
            {
                String method = "";
                String temp[] = DataVisitor.methods.get(i).split("/");
                String temp2[] = temp[0].split("[.]");
                String temp3[] = temp[0].split("\\s");
                //then print any methods left of your own
//                System.out.println(printed + " "+temp2[1] +" "+ printed.contains(temp2[1]));
                if(!printed.contains(temp2[1]))
                    if (temp3[0].equals(cl) || temp2[0].equals(cl))
                    {
                        counter++;
                        if (found == 0) {
                            //                    retStr += "\n";
                            found++;
                        } else
                            retStr += ",";
                        retStr += "\n\t" + "i8* bitcast (";
                        if (temp[1].equals("int")) {
                            retStr += "i32 ";
                            method += "i32 ";
                        }else if (temp[1].equals("int[]")) {
                            retStr += "i32* ";
                            method += "i32* ";
                        }else if (temp[1].equals("boolean")) {
                            retStr += "i1 ";
                            method += "i1 ";
                        }else {
                            retStr += "i8* ";
                            method += "i8* ";
                        }
                        retStr += "(i8*";
                        method += "(i8*";
                        if (DataVisitor.parameters.containsKey(temp[0])) {
                            String temp4[] = DataVisitor.parameters.get(temp[0]).values().toArray(new String[0]);
                            for (int j = 0; j < temp4.length; j++) {
                                String temp5[] = temp4[j].split("\\s");//type
                                if (temp5[0].equals("int")) {
                                    retStr += ",i32";
                                    method += ",i32";
                                }else if (temp5[0].equals("int[]")) {
                                    retStr += ",i32*";
                                    method += ",i32*";
                                }else if (temp5[0].equals("boolean")) {
                                    retStr += ",i1";
                                    method += ",i1";
                                }else {
                                    retStr += ",i8*";
                                    method += ",i8*";
                                }
                            }
                        }
                        String tmp = cl;
                        counter1 = 1;
                        while (true) {
//                            System.out.println(cl + " yooo " + ancestors + " yooo " + realAncestors.get(realAncestors.size() - counter1) + "." + temp2[1] + "/" + temp[1] + "/" + temp[2]);
                            if (DataVisitor.methods.contains(realAncestors.get(realAncestors.size() - counter1) + "." + temp2[1] + "/" + temp[1] + "/" + temp[2]))
                                tmp = ancestors.get(ancestors.size() - counter1);
                            counter1++;
                            if (realAncestors.size() - counter1 < 0)
                                break;
                        }
//                        System.out.println(cl + " yooo " + ancestors + " yooo " + myClass + "." + temp2[1] + "/" + temp[1] + "/" + temp[2]);

                        if (DataVisitor.methods.contains(realClass + "." + temp2[1] + "/" + temp[1] + "/" + temp[2]))
                            tmp = myClass;
                        retStr += ")* @" + tmp + "." + temp2[1] + " to i8*)";
                        method += ")*";
                        vtablemethodtype.get(myClass).put(temp2[1], method);
                    }
            }
//            }
        }
        if(found>0)
            retStr += "\n";
        retStr = retStr + "]\n\n";
        retStr = String.format("@.%s_vtable = global [%d x i8*] [", cl, counter) + retStr;
        vtabletype.put(cl,String.format("[%d x i8*]", counter));
        return retStr;
    }

    public static String getVtableMethodType(String myClass, String method)
    {
        System.out.println("getvtable class:"+myClass+" method:"+method);
        String[] temp = myClass.split("\\s");
        if(temp.length>1 && temp[1].equals("extends"))
            myClass = temp[0];
        return vtablemethodtype.get(myClass).get(method);
    }

    public static String getVtableMethodReturnType(String myClass, String method)
    {
        String[] temp = myClass.split("\\s");
        if(temp.length>1 && temp[1].equals("extends"))
            myClass = temp[0];
        String[] temp2 = vtablemethodtype.get(myClass).get(method).split("[(]");
        return temp2[0];
    }

    public static String getVtable(String object)
    {
        String[] tempc = object.split("[.]");
        if(tempc.length>1 && !tempc[1].isEmpty())
            object = tempc[0];
        String[] name = object.split("\\s");
        if(name.length>1 && name[1].equals("extends"))
            object = name[0];
        if(DataVisitor.simpleClasses.contains(object))
            return "@."+object+"_vtable";
        else{
            System.out.println("Helper.getVtable error");
            System.exit(1);
        }
        return null;
    }

    public static String LLHeader()
    {
        return "\n;Everything below up until main is boilerplate and will be included in all\n"
    + ";your outputs.\n\n"
    + "declare i8* @calloc(i32, i32)\n"
    + "declare i32 @printf(i8*, ...)\n"
    + "declare void @exit(i32)\n\n"
    + "@_cint = constant [4 x i8] c\"%d\\0a\\00\"\n"
    + "@_cOOB = constant [15 x i8] c\"Out of bounds\\0a\\00\"\n"
    + "@_cNSZ = constant [15 x i8] c\"Negative size\\0a\\00\"\n\n"
    + "define void @print_int(i32 %i) {\n"
    + "\t%_str = bitcast [4 x i8]* @_cint to i8*\n"
    + "\tcall i32 (i8*, ...) @printf(i8* %_str, i32 %i)\n"
    + "\tret void\n"
    + "}\n\n"
    + "define void @throw_oob() {\n"
    + "\t%_str = bitcast [15 x i8]* @_cOOB to i8*\n"
    + "\tcall i32 (i8*, ...) @printf(i8* %_str)\n"
    + "\tcall void @exit(i32 1)\n"
    + "\tret void\n"
    + "}\n\n"
    + "define void @throw_nsz() {\n"
    + "\t%_str = bitcast [15 x i8]* @_cNSZ to i8*\n"
    + "\tcall i32 (i8*, ...) @printf(i8* %_str)\n"
    + "\tcall void @exit(i32 1)\n"
    + "\tret void\n"
    + "}\n\n\n";
    }

    public static int isField(String classname, String variable)
    {
        ArrayList<String> ancestors = new ArrayList<String>();
        ArrayList<String> realAncestors = new ArrayList<String>();
        String parentClass;
        String[] tempc = classname.split("[.]");
        if(tempc.length>1 && !tempc[1].isEmpty())
            classname = tempc[0];
        String simpleClassname = classname;
        String realClassname = "";
//        System.out.println("\n;ISFIELD "+simpleClassname+" "+variable);

//        Generator.emit += "\n;is "+variable +"of"+simpleClassname+"?";
        if(DataVisitor.classes.contains(classname))
            realClassname = classname;
        else{
            for(int i=0; i<DataVisitor.classes.size();i++){
                String[] tempor = DataVisitor.classes.get(i).split("\\s");
                if(tempor[0].equals(simpleClassname))
                    realClassname = DataVisitor.classes.get(i);
            }
        }
        String[] name = realClassname.split("\\s");

        int done = 0;
        if(name.length>1 && name[1].equals("extends"))
        {
            parentClass = name[2];
            ancestors.add(name[2]);
            simpleClassname = name[0];
            if(DataVisitor.classes.contains(parentClass))
                realAncestors.add(name[2]);
            else{
                while(true) {
                    for (int j = 0; j < DataVisitor.classes.size(); j++) {
                        name = DataVisitor.classes.get(j).split("\\s");
//                        System.out.println("for"+DataVisitor.classes.size()+" "+name[0]+"  " +parentClass);

                        if (name[0].equals(parentClass)) {
//                            System.out.println("forif");

                            parentClass = name[2];
                            if(ancestors.contains(name[2])) {
                                done = 1;
                                break;
                            }
                            ancestors.add(name[2]);
                            realAncestors.add(DataVisitor.classes.get(j));
                            break;
                        }
                    }
                    if (DataVisitor.classes.contains(parentClass)) {
//                        System.out.println("if");

                        if(realAncestors.contains(name[2])) {
                            done = 1;
                            break;
                        }
                        realAncestors.add(name[2]);
                        break;
                    }
                    if (done==1)
                        break;
                }
            }
        }

//        List<String> myMethodOffsets = new ArrayList<String>(methodOffsets.get(simpleClassname).values());
//        List<String> myFieldOffsets = new ArrayList<String>(fieldOffsets.get(simpleClassname).values());
//        String val;
//        methodOffsets.get(simpleClassname).forEach((key, value) -> System.out.println(key + " : " + value));
//        fieldOffsets.get(simpleClassname).forEach((key, value) -> System.out.println(key + " : " + value));
//        System.out.println("\n; methodOffsets of " + simpleClassname);
//        if(methodOffsets.containsKey(simpleClassname))
//            Generator.emit += methodOffsets.get(simpleClassname).toString();
        if(getKey( methodOffsets.get(simpleClassname), variable)!=-1){System.out.println("\n;returning");
            return getKey( methodOffsets.get(simpleClassname), variable);}
        if(getKey( fieldOffsets.get(simpleClassname), variable)!=-1){System.out.println("\n;returning");
            return getKey( fieldOffsets.get(simpleClassname), variable);}
//
//        for(int i=0; i<myFieldOffsets.size();i++)
//            System.out.printf(","+i+": " + myFieldOffsets.get(i));
//        System.out.println();
//
//        for(int i=0; i<myMethodOffsets.size();i++)
//            System.out.printf(","+i+": " + myMethodOffsets.get(i));
//        System.out.println();

//        System.out.printf("ancestors of "+classname+" = ");
//        for(int i=0; i<)
        System.out.println("\n ancestors of "+simpleClassname);
        for(int i=0;i<ancestors.size();i++) {
            System.out.printf(" "+ancestors.get(i));
            if(getKey( methodOffsets.get(ancestors.get(i)), variable)!=-1)
                return getKey( methodOffsets.get(ancestors.get(i)), variable);
            if(getKey( fieldOffsets.get(ancestors.get(i)), variable)!=-1)
                return getKey( fieldOffsets.get(ancestors.get(i)), variable);
//            System.out.printf(" " + ancestors.get(i));
        }
//        System.out.println();
        return -1;
    }

    public static boolean isMethodVariable(String method, String variable)
    {
        List<String> myMethodVariables;
//        Generator.emit += "\n; isMethodVariable "+method+" "+variable;
        variable = variable.trim();
        if(method.equals(DataVisitor.mainClass)){
            myMethodVariables = new ArrayList<String>(DataVisitor.methodVariables.get(DataVisitor.mainClass+".main").values());
            for(int j=0; j<myMethodVariables.size();j++) {
//                Generator.emit += "\n;"+myMethodVariables.get(j)+" "+variable;
                String[] l = myMethodVariables.get(j).split("\\s");
                if (l[1]!=null && l[1].equals(variable)) {
//                    Generator.emit += "\n;found";
                    return true;
                }
            }
        }
        for(int i=0;i<DataVisitor.methods.size();i++){
            String[] m = DataVisitor.methods.get(i).split("/");
//            Generator.emit += "\n"+m[0];
            if(m[0].equals(method)) {
                myMethodVariables = new ArrayList<String>(DataVisitor.methodVariables.get(m[0]).values());
//                Generator.emit += "\n; " + myMethodVariables;
                for(int j=0; j<myMethodVariables.size();j++) {
//                    Generator.emit += "\n;"+myMethodVariables.get(j)+" "+variable;
                    String[] l = myMethodVariables.get(j).split("\\s");
                    if (l[1]!=null && l[1].equals(variable)) {
//                       Generator.emit += "\n;found";
                        return true;
                    }
                }
            }
        }
//        System.out.println(myMethodVariables);
        return false;
    }

    public static int getSizeOfObject(String object)
    {
        ArrayList<String> ancestors = new ArrayList<String>();
        ArrayList<String> realAncestors = new ArrayList<String>();
        String simpleClassname = object;
        String parentClass;
        String[] tempc = object.split("[.]");
        if(tempc.length>1 && !tempc[1].isEmpty())
            object = tempc[0];
        String[] name = object.split("\\s");
        int done = 0;
        if(name.length>1 && name[1].equals("extends"))
        {
            parentClass = name[2];
            ancestors.add(name[2]);
            simpleClassname = name[0];
            if(DataVisitor.classes.contains(parentClass))
                realAncestors.add(name[2]);
            else{
                while(true) {
                    for (int j = 0; j < DataVisitor.classes.size(); j++) {
                        name = DataVisitor.classes.get(j).split("\\s");
                        if (name[0].equals(parentClass)) {
                            parentClass = name[2];
                            if(ancestors.contains(name[2])) {
                                done = 1;
                                break;
                            }
                            ancestors.add(name[2]);
                            realAncestors.add(DataVisitor.classes.get(j));
                            break;
                        }
                    }
                    if (DataVisitor.classes.contains(parentClass)) {
                        if(realAncestors.contains(name[2])) {
                            done = 1;
                            break;
                        }
                        realAncestors.add(name[2]);
                        break;
                    }
                    if (done==1)
                        break;
                }
            }
        }
//        if(getKey( methodOffsets.get(simpleClassname), variable)!=-1)
//            return getKey( methodOffsets.get(simpleClassname), variable);
//        if(getKey( fieldOffsets.get(simpleClassname), variable)!=-1)
//            return getKey( fieldOffsets.get(simpleClassname), variable);
        int booleanSpace = 1;
        int integerSpace = 4;
        int pointerSpace = 8;
        int maxKey = 0;
        System.out.println(fieldOffsets);
        System.out.println(methodOffsets);
        if(fieldOffsets.containsKey(simpleClassname))
            if(!fieldOffsets.get(simpleClassname).isEmpty()) {
                maxKey = Collections.max(fieldOffsets.get(simpleClassname).keySet());
                String[] lastItem = fieldOffsets.get(simpleClassname).get(maxKey).split("\\s");
                if (lastItem[0].equals("int"))
                    maxKey += integerSpace;
                else if (lastItem[0].equals("boolean"))
                    maxKey += booleanSpace;
                else
                    maxKey += pointerSpace;

            }
        return maxKey;
    }

    public static ArrayList<String> findAncestors(String myClass)
    {
        String cl = null;
        String realClass = null;
        String parentClass = null;
        ArrayList<String> ancestors = new ArrayList<String>();
        ArrayList<String> realAncestors = new ArrayList<String>();
        if(DataVisitor.classes.contains(myClass))
            return null;
        else
        {
            for(int i=0;i<DataVisitor.classes.size();i++)
            {
                String temp[] = DataVisitor.classes.get(i).split("\\s");
                if(temp[0].equals(myClass))
                {
                    realClass = DataVisitor.classes.get(i);
                    parentClass = temp[2];
                    ancestors.add(temp[2]);
//                    System.out.println("parentclass of "+myClass+"="+parentClass);
                    if(DataVisitor.classes.contains(parentClass))
                        realAncestors.add(temp[2]);
                    else {
                        while (true) {
                            for (int j = 0; j < DataVisitor.classes.size(); j++) {
                                temp = DataVisitor.classes.get(j).split("\\s");
                                if (temp[0].equals(parentClass)) {
                                    parentClass = temp[2];
                                    ancestors.add(temp[2]);
                                    realAncestors.add(DataVisitor.classes.get(j));
                                    break;
                                }
                            }
                            if (DataVisitor.classes.contains(parentClass)) {
                                realAncestors.add(temp[2]);
                                break;
                            }
                        }
                    }
                    break;
                }
            }
        }
        return ancestors;
    }

    public static void printOutput() throws Exception
    {
        List<String> table;
        String[] result;
        String[] name;
        String[] tmp;
        int[] variableCounter = new int[DataVisitor.classes.size()];
        Arrays.fill(variableCounter, 0);
        int[] methodCounter = new int[DataVisitor.classes.size()];
        Arrays.fill(methodCounter, 0);
        int booleanSpace = 1;
        int integerSpace = 4;
        int pointerSpace = 8;
        int vCounter = 0;
        int mCounter = 0;
        for(int i=0;i<DataVisitor.classes.size();i++)
        {
            if(DataVisitor.classes.get(i).equals(DataVisitor.mainClass))
                continue;
            Map<Integer,String> methodInner = new TreeMap<>();
            Map<Integer,String> fieldInner = new TreeMap<>();
            table = new ArrayList<String>(DataVisitor.symbolTable.get(DataVisitor.classes.get(i)).values());
            if(!table.isEmpty())
            {
                name = DataVisitor.classes.get(i).split("\\s");
                //System.out.println(table);
                vCounter = 0;
                mCounter = 0;
                if(name.length>1){
//                        System.out.println(eval.classes.get(i));
//                        System.out.println(eval.classes.indexOf(name[2]) + " found " + name[2]);
                    if(name[1].equals("extends"))
                    {
                        methodOffsets.put(name[0], methodInner);
                        fieldOffsets.put(name[0], fieldInner);
                        int index = DataVisitor.classes.indexOf(name[2]);
                        if(index==-1)
                            for(int k=0;k<DataVisitor.classes.size();k++)
                            {
                                tmp = DataVisitor.classes.get(k).split("\\s");
                                if(tmp.length>2)
                                    if(tmp[0].equals(name[2]))
                                        index = k;
                            }
                        if(index==-1) {
                            System.out.println("ERROR: class being extended does not exist");
//                            System.exit(1);
                            throw new ParseException("ERROR");
                        }
                        vCounter = variableCounter[index];
                        mCounter = methodCounter[index];
                    }
                }
                else {
                    methodOffsets.put(DataVisitor.classes.get(i), methodInner);
                    fieldOffsets.put(DataVisitor.classes.get(i), fieldInner);
                }
                for(int j=0;j<table.size();j++)
                {
                    result = table.get(j).split("\\s");
                    if(!result[0].equals("method")) {
//                        System.out.println(name[0] + "." + result[1] + " : " + vCounter);
                        fieldInner.put(vCounter, result[0] + " " + result[1]);
                    }
                    else {
                        if(name.length>1)
                        {
                            if(name[1].equals("extends"))
                            {
//                                    System.out.println(eval.symbolTable.get(name[2]));
                                String ar = null;
                                List<String> temp;
                                if(DataVisitor.symbolTable.get(name[2])==null) {
                                    for (int k = 0; k < DataVisitor.classes.size(); k++) {
                                        tmp = DataVisitor.classes.get(k).split("\\s");
                                        if (tmp.length > 2)
                                            if (tmp[0].equals(name[2]))
                                                ar = DataVisitor.classes.get(k);
                                    }
                                    if(ar!=null)
                                        temp = new ArrayList<String>(DataVisitor.symbolTable.get(ar).values());
                                    else
                                        continue;
                                }else
                                    temp = new ArrayList<String>(DataVisitor.symbolTable.get(name[2]).values());
                                int found=0;
                                for(int k=0;k<temp.size();k++)
                                {
                                    tmp = temp.get(k).split("\\s");
                                    if(tmp.length>2)
                                        if(tmp[2].equals(result[2]))
                                            found=1;
                                }
                                if(found==0) {
//                                    System.out.println(name[0] + "." + result[2] + " : " + mCounter);
                                    methodInner.put(mCounter, result[1] + " " + result[2]);
                                    mCounter += pointerSpace;
                                }
                            }
                        }
                        else {
//                            System.out.println(name[0] + "." + result[2] + " : " + mCounter);
                            methodInner.put(mCounter, result[1] + " " + result[2]);
                            mCounter += pointerSpace;
                        }
                    }
//                        System.out.println(eval.symbolTables.get(i) + "." + result[0] + " " + result[1] + " : " + counter);
                    if(!result[0].equals("method")) {
                        if (result[0].equals("int"))
                            vCounter += integerSpace;
                        else if (result[0].equals("boolean"))
                            vCounter += booleanSpace;
                        else
                            vCounter += pointerSpace;
//                        System.out.println(counter);
                    }
                }
                variableCounter[i] = vCounter;
                methodCounter[i] = mCounter;
            }
            table.clear();
        }
    }

    public static boolean hasType(String str)
    {
        String[] temp = str.split("\\s");
        if(!str.startsWith("i"))
            return false;
        else
            return true;
    }

    public static void doubleDeclarationCheck() throws Exception
    {
        String[] tmp;
        //check if a variable or method is declared more than once
        for(int i = 0; i< DataVisitor.symbolTables.size(); i++)
        {
            List<String> temp1;
            temp1 = new ArrayList<String>(DataVisitor.symbolTable.get(DataVisitor.symbolTables.get(i)).values());
            for(int j=0;j<temp1.size();j++)
            {
                int occurrences = Collections.frequency(temp1, temp1.get(j));
                String[] temp2 = temp1.get(j).split("\\s");
                int found = 0;
                for (int k = 0; k < temp1.size(); k++) {
                    tmp = temp1.get(k).split("\\s");
                    if (tmp[1].equals(temp2[1]) && !tmp[0].equals("method") && !temp2[0].equals("method")) {
//                            System.out.println("Ey " + temp1.get(k) + " " + temp1.get(j));
                        found++;
                    }
                    if(found==0 && tmp.length>2 && temp2.length>2)
                        if (tmp[2].equals(temp2[2]) && tmp[0].equals("method") && temp2[0].equals("method")) {
//                                System.out.println("Ey " + temp1.get(k) + " " + temp1.get(j));
                            found++;
                        }
                }
                if (occurrences > 1 || found > 1) {
                    System.out.println("ERROR: declared more than once");
//                    System.exit(1);
                    throw new ParseException("ERROR");
                }

            }
            //System.out.println(eval.symbolTables.get(i));
        }
        //check if a class is declared more than once
        for(int i = 0; i< DataVisitor.classes.size(); i++)
        {
            String[] temp2 = DataVisitor.classes.get(i).split("\\s");
            int occurrences = Collections.frequency(DataVisitor.classes, DataVisitor.classes.get(i));
            int found = 0;
            for (int k = 0; k < DataVisitor.classes.size(); k++) {
                tmp = DataVisitor.classes.get(k).split("\\s");
                if (tmp[0].equals(temp2[0]))
                    found++;
            }
            if (occurrences > 1 || found > 1) {
                System.out.println("ERROR: class declared more than once");
//                System.exit(1);
                throw new ParseException("ERROR");
            }
        }
    }

    public static void methodsCheck() throws Exception
    {
        //check methods (for right parameters and return type if overriding, for declared return type and actual return value)
        String[] tmp;
        for(int i = 0; i< DataVisitor.methods.size(); i++)
        {
            int retsuccess = 0;
            String[] method = DataVisitor.methods.get(i).split("/");
//            if(method.length>2)
            if(method[1].equals(method[2])||(isAssignable(method[1],method[2])==1))
                retsuccess++;
            String[] tmp5 = method[2].split("\\s");
            if(tmp5.length>2 && tmp5[0].equals("new")) {
                if(method[1].equals(tmp5[1])||(isAssignable(method[1],tmp5[1])==1))
                    retsuccess++;
                if(tmp5[2].equals("[]"))
                    if(method[1].equals(tmp5[1]+"[]"))
                        retsuccess++;
            }
//                System.out.println(method[0]);
            //check for declared return type and actual return type
//                System.out.println("methodMan '" + method[0] + "' " + method[1]+" "+method[2]);
            if(method[1].equals("int")){
                if(method[2].equals("int")){
                    retsuccess = 1;
                }
            }
            if(method[1].equals("boolean")){
                if(method[2].equals("boolean")){
                    retsuccess = 1;
                }
            }
            if(retsuccess==0)
            {
                tmp = method[0].split("[.]");
                String tmp2 = tmp[0];
                List<String> temp2 = new ArrayList<String>(DataVisitor.symbolTable.get(method[0]).values());
                List<String> temp3 = new ArrayList<String>(DataVisitor.symbolTable.get(tmp2).values());
                int found = 0;
                //search the variables declared inside the method
                for(int k=0;k<temp2.size();k++)
                {
                    tmp = (temp2.get(k)).split("\\s");
                    if(tmp.length>1)
                        if(method[2].equals(tmp[1]) && !tmp[0].equals("method"))
                        {
//                            System.out.println(temp2.get(k));
                            found++;
                            if(method[1].equals(tmp[0]))
                                retsuccess = 1;
                        }
                    tmp = null;
                }
                //search the variables declared inside the class
                if(found==0)
                {
                    for(int k=0;k<temp3.size();k++)
                    {
                        tmp = (temp3.get(k)).split("\\s");
                        if(tmp.length>1)
                            if(method[2].equals(tmp[1]) && !tmp[0].equals("method"))
                            {
//                                System.out.println(temp3.get(k));
                                found++;
                                if(method[1].equals(tmp[0]))
                                    retsuccess = 1;
                            }
                        tmp = null;
                    }
                }
                //search the variables declared in the parent class if it exists
                int parentIndex = -1;
                if(found==0)
                {
                    tmp = method[0].split("[.]");
                    String[] tmp3 = tmp[0].split("\\s");
                    String[] tmp4;
                    if (tmp3.length > 2)    //else there is no parent class
                    {
                        parentIndex = DataVisitor.classes.indexOf(tmp3[2]);
                        if(parentIndex==-1)
                            for(int k = 0; k< DataVisitor.classes.size(); k++)
                            {
                                tmp4 = DataVisitor.classes.get(k).split("\\s");
                                if(tmp4.length>2)
                                    if(tmp4[0].equals(tmp3[2]))
                                        parentIndex = k;
                            }
                        if(parentIndex==-1)
                            break;
                        temp3 = new ArrayList<String>(DataVisitor.symbolTable.get(DataVisitor.classes.get(parentIndex)).values());
                        for(int k=0;k<temp3.size();k++)
                        {
                            tmp = (temp3.get(k)).split("\\s");
                            if(tmp.length>1)
                                if(method[2].equals(tmp[1]) && !tmp[0].equals("method"))
                                {
                                    found++;
                                    if(method[1].equals(tmp[0]))
                                        retsuccess = 1;
                                }
                            tmp = null;
                        }
                    }
                }
                //search deeper in ancestors
                while(found==0)
                {
                    if(parentIndex!=-1)
                    {
                        String[] tmp3 = DataVisitor.classes.get(parentIndex).split("\\s");
//                            System.out.println(eval.classes.get(parentIndex));
                        String[] tmp4;
                        if (tmp3.length > 2)    //else there is no parent class
                        {
                            parentIndex = DataVisitor.classes.indexOf(tmp3[2]);    //parentOfparent
                            if (parentIndex == -1)
                                for (int k = 0; k < DataVisitor.classes.size(); k++) {
                                    tmp4 = DataVisitor.classes.get(k).split("\\s");
                                    if (tmp4.length > 2)
                                        if (tmp4[0].equals(tmp3[2]))
                                            parentIndex = k;
                                }
                            if (parentIndex == -1)
                                break;
                            temp3 = new ArrayList<String>(DataVisitor.symbolTable.get(DataVisitor.classes.get(parentIndex)).values());
                            for (int k = 0; k < temp3.size(); k++) {
                                tmp = (temp3.get(k)).split("\\s");
                                if (tmp.length > 1)
                                    if(method[2].equals(tmp[1]) && !tmp[0].equals("method"))
                                    {
                                        found++;
                                        if(method[1].equals(tmp[0]))
                                            retsuccess = 1;
                                    }
                                tmp = null;
                            }
                        }
                        else break;
                    }
                    else break;
                }
            }
            if(retsuccess==0) {
                System.out.println("ERROR: wrong return type ");
//                System.exit(1);
                throw new ParseException("ERROR");
            }
            //end of return type check

            //for overriding, check if return type is equal to parent method, and if parameters are the same
            tmp = method[0].split("[.]");
            String parentMethod = tmp[1];
            tmp = tmp[0].split("\\s");
            int paramsuccess = 1;
            retsuccess = 1;
            if(tmp.length>1)            //if there exists a parent
            {
                if(tmp[1].equals("extends"))
                {
                    String parentClass = tmp[2];
                    String par2 = parentClass + "." + parentMethod;
//                        System.out.println("Ey " + parentClass + "." + parentMethod);
                    for(int j = 0; j< DataVisitor.methods.size(); j++)
                    {
                        String[] temp4 = DataVisitor.methods.get(j).split("/");
                        if(temp4[0].equals(par2)){          //found parent method which is getting overriden
                            paramsuccess = 0;
                            retsuccess = 0;
                            List<String> myParameters = new ArrayList<String>(DataVisitor.parameters.get(method[0]).values());
                            List<String> parentParameters = new ArrayList<String>(DataVisitor.parameters.get(temp4[0]).values());
                            if(myParameters.size()==parentParameters.size()){
                                for(int k=0;k<myParameters.size();k++)
                                {
                                    String[] myprm = myParameters.get(k).split("\\s");
                                    String[] prntprm = parentParameters.get(k).split("\\s");
//                                        System.out.println("PARAMETERS " + myParameters.get(k)+ " " + parentParameters.get(k));
                                    if(myprm[0].equals(prntprm[0]))
                                        paramsuccess++;
                                }
                                if(paramsuccess<myParameters.size())
                                    paramsuccess=0;
                                else
                                    paramsuccess=1;
                            }
                            if(method[1].equals(temp4[1]))
                                retsuccess=1;
                            break;
                        }
                        else{
                            String[] temp5 = temp4[0].split("[.]");
                            String[] temp6 = temp5[0].split("\\s");
                            String par = temp6[0] + "." + temp5[1];
                            if(par.equals(par2)){           //found parent method which is getting overriden
                                paramsuccess = 0;
                                retsuccess = 0;
                                List<String> myParameters = new ArrayList<String>(DataVisitor.parameters.get(method[0]).values());
                                List<String> parentParameters = new ArrayList<String>(DataVisitor.parameters.get(temp4[0]).values());
                                if(myParameters.size()==parentParameters.size()){
                                    for(int k=0;k<myParameters.size();k++)
                                    {
                                        String[] myprm = myParameters.get(k).split("\\s");
                                        String[] prntprm = parentParameters.get(k).split("\\s");
                                        if(myprm[0].equals(prntprm[0]))
                                            paramsuccess++;
                                    }
                                    if(paramsuccess<myParameters.size())
                                        paramsuccess=0;
                                    else
                                        paramsuccess=1;
                                }
                                if(method[1].equals(temp4[1]))
                                    retsuccess=1;
                                break;
                            }
                        }
                    }
                }
            }
            if(paramsuccess==0){
                System.out.println("ERROR: overriding method has wrong parameters");
//                System.exit(1);
                throw new ParseException("ERROR");
            }
            if(retsuccess==0){
                System.out.println("ERROR: overriding method has wrong return type");
//                System.exit(1);
                throw new ParseException("ERROR");
            }

//                System.out.println("class " + tmp[0] + " length = " + tmp.length);
            //end of overriding check
        }
    }

    public static int isInt(String str) throws Exception
    {
        int isint = 2;
        String[] temp = str.split("\\s");
        if(temp.length>1 && temp[0].startsWith("i")) {
            try {
                Integer.parseInt(temp[temp.length - 1]);
            } catch (Exception ex) {
                isint = 1;
            }
            if (isint==2)
                return 2;
        }
        isint=1;
        try{Integer.parseInt(str);}
        catch(Exception ex){isint=0;}
        return isint;
    }

    public static int isVariableRegister(String str)
    {
        if(str.startsWith("%"))
            return 1;
        String[] temp = str.split("\\s");
        String care1 = "";
        String care2 = "";
        for(int i=0;i<temp.length;i++) {
            if (!temp[i].equals("") && care1.equals(""))
                care1 = temp[i];
            else if (!temp[i].equals("") && care2.equals(""))
                care2 = temp[i];
        }
        if(care1.startsWith("i") && care2.startsWith("%"))
            return 2;
        return 0;
    }

    public static int isAssignable(String superclass, String subclass) throws Exception
    {
//        System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!! " + superclass + " " + subclass);
        String[] splitsub = subclass.split("\\s");
        if(splitsub[0].equals("new")){
            if(superclass.equals("int[]")&&splitsub[1].equals("int")&&splitsub[2].equals("[]")){
                return 1;}
            if(isAssignable(superclass,splitsub[1])==1)
                return 1;
        }
        if(superclass.equals(subclass))
            return 1;
        int subIndex = -1;
        int superIndex = -1;
        int parentIndex = -1;
        int found=0;
        superIndex = DataVisitor.classes.indexOf(superclass);
        subIndex = DataVisitor.classes.indexOf(subclass);
        if(superIndex==-1)
            for(int k = 0; k< DataVisitor.classes.size(); k++)
            {
                String[] tmp = DataVisitor.classes.get(k).split("\\s");
                if(tmp.length>2)
                    if(tmp[0].equals(superclass))
                        superIndex = k;
            }
        if(subIndex==-1)
            for(int k = 0; k< DataVisitor.classes.size(); k++) {
                String[] tmp = DataVisitor.classes.get(k).split("\\s");
                if (tmp.length > 2)
                    if (tmp[0].equals(subclass))
                        subIndex = k;
            }
        /*if(superIndex==-1 && subIndex==-1) {
            System.out.println(superclass.class.isAssignableFrom(subclass.class));
            if (superclass.class.isAssignableFrom(subclass.class))
                return 1;
            else
                return 0;
        }
        else*/ if(superIndex!=-1 && subIndex!=-1){
        String[] tmp3 = DataVisitor.classes.get(subIndex).split("\\s");
        String[] tmp4;
        if (tmp3.length>2)    //else there is no parent class
        {
            parentIndex = DataVisitor.classes.indexOf(tmp3[2]);
            if(parentIndex==-1)
                for(int k = 0; k< DataVisitor.classes.size(); k++)
                {
                    tmp4 = DataVisitor.classes.get(k).split("\\s");
                    if(tmp4.length>2)
                        if(tmp4[0].equals(tmp3[2])) {
                            parentIndex = k;
                            if(tmp4[0].equals(superclass))
                                found=1;
                        }
                }
        } else return 0;
//            System.out.println("1here " + eval.classes.get(parentIndex) + found);
        while(found==0)
        {
            if(parentIndex!=-1)
            {
                tmp3 = DataVisitor.classes.get(parentIndex).split("\\s");
//                    System.out.println("2here " + eval.classes.get(parentIndex) + found);
                if (tmp3.length>2)    //else there is no parent class
                {
                    parentIndex = DataVisitor.classes.indexOf(tmp3[2]);    //parentOfparent
                    if (parentIndex == -1)
                        for (int k = 0; k < DataVisitor.classes.size(); k++) {
                            tmp4 = DataVisitor.classes.get(k).split("\\s");
                            if (tmp4.length > 2)
                                if (tmp4[0].equals(tmp3[2])) {
                                    parentIndex = k;
                                    if (tmp4[0].equals(superclass))
                                        found = 1;
                                }
                                else if(DataVisitor.classes.get(k).equals(superclass))
                                    found=1;
                        }
                    if (parentIndex == -1)
                        break;
                }
                else if(DataVisitor.classes.get(parentIndex).equals(superclass))
                    found=1;
                else break;
            }
            else break;
        }
    }
//        System.out.println("foundClass " + found);
        return found;

    }

    public static boolean typeOfVariable(String myClass, String myMethod, String type, String variable) throws Exception
    {
//        System.out.println("typeOfVariable " + myClass + " " + myMethod + " " + type+ " "+ variable);
        if(type==null)
            return false;
        String[] tmp;
        String tmp2 = myClass;
        int methodcall = 0;
        if(myMethod!=null) {
            tmp = myMethod.split("[.]");
            tmp2 = tmp[0];
        }
        if(variable.equals("this"))
        {
            String[] c = tmp2.split("\\s");
            if(c.length>1) {
                if (c[0].equals(type))
                    return true;
            }
            else {
                if(tmp2.equals(type))
                    return true;
            }
        }
        List<String> temp3 = new ArrayList<String>(DataVisitor.symbolTable.get(tmp2).values());
        int found = 0;
        //search the variables declared inside the method
        if(myMethod!=null)
        {
            List<String> temp2 = new ArrayList<String>(DataVisitor.symbolTable.get(myMethod).values());
            for (int k = 0; k < temp2.size(); k++) {
                tmp = (temp2.get(k)).split("\\s");
                if (tmp.length > 1)
                    if (variable.equals(tmp[1])) {
                        found++;
                        if(type.equals(tmp[0])||(type.equals("new int []")&&tmp[0].equals("int[]")))
                            return true;
                        else if(isAssignable(tmp[0],type)==1)
                            return true;
                        else{
                            String[] type1 = type.split("\\s");
                            if( type1.length>2 && type1[0].equals("new") && ( type1[1].equals(tmp[0]) || (isAssignable(tmp[0], type1[1])==1) ) )
                                return true;
                        }
                    }
                tmp = null;
            }
        }
        //search the variables declared inside the class
        if(found==0)
        {
            for(int k=0;k<temp3.size();k++)
            {
                tmp = (temp3.get(k)).split("\\s");
                if(tmp.length>1)
                    if (variable.equals(tmp[1])) {
                        found++;
                        if(type.equals(tmp[0])||(type.equals("new int []")&&tmp[0].equals("int[]")))
                            return true;
                        else if(isAssignable(tmp[0],type)==1)
                            return true;
                        else{
                            String[] type1 = type.split("\\s");
                            if( type1.length>2 && type1[0].equals("new") && ( type1[1].equals(tmp[0]) || (isAssignable(tmp[0], type1[1])==1) ) )
                                return true;
                        }
                    }
                tmp = null;
            }
        }

        //search the variables declared in the parent class if it exists
        int parentIndex = -1;
        if(found==0)
        {
            tmp = myMethod.split("[.]");
            String[] tmp3 = tmp[0].split("\\s");
            String[] tmp4;
            if (tmp3.length > 2)    //else there is no parent class
            {
                parentIndex = DataVisitor.classes.indexOf(tmp3[2]);
                if(parentIndex==-1)
                    for(int k = 0; k< DataVisitor.classes.size(); k++)
                    {
                        tmp4 = DataVisitor.classes.get(k).split("\\s");
                        if(tmp4.length>2)
                            if(tmp4[0].equals(tmp3[2]))
                                parentIndex = k;
                    }
                temp3 = new ArrayList<String>(DataVisitor.symbolTable.get(DataVisitor.classes.get(parentIndex)).values());
                for(int k=0;k<temp3.size();k++)
                {
                    tmp = (temp3.get(k)).split("\\s");
                    if(tmp.length>1)
                        if (variable.equals(tmp[1])) {
                            found++;
                            if(type.equals(tmp[0])||(type.equals("new int []")&&tmp[0].equals("int[]")))
                                return true;
                            else if(isAssignable(tmp[0],type)==1)
                                return true;
                            else{
                                String[] type1 = type.split("\\s");
                                if( type1.length>2 && type1[0].equals("new") && ( type1[1].equals(tmp[0]) || (isAssignable(tmp[0], type1[1])==1) ) )
                                    return true;
                            }
                        }
                    tmp = null;
                }
            }
        }
        //search deeper in ancestors
        while(found==0)
        {
            if(parentIndex!=-1)
            {
                String[] tmp3 = DataVisitor.classes.get(parentIndex).split("\\s");
//                            System.out.println(eval.classes.get(parentIndex));
                String[] tmp4;
                if (tmp3.length > 2)    //else there is no parent class
                {
                    parentIndex = DataVisitor.classes.indexOf(tmp3[2]);    //parentOfparent
                    if (parentIndex == -1)
                        for (int k = 0; k < DataVisitor.classes.size(); k++) {
                            tmp4 = DataVisitor.classes.get(k).split("\\s");
                            if (tmp4.length > 2)
                                if (tmp4[0].equals(tmp3[2]))
                                    parentIndex = k;
                        }
                    if (parentIndex == -1)
                        break;
                    temp3 = new ArrayList<String>(DataVisitor.symbolTable.get(DataVisitor.classes.get(parentIndex)).values());
                    for (int k = 0; k < temp3.size(); k++) {
                        tmp = (temp3.get(k)).split("\\s");
                        if(tmp.length>1)
                            if (variable.equals(tmp[1])) {
                                found++;
                                if(type.equals(tmp[0])||(type.equals("new int []")&&tmp[0].equals("int[]")))
                                    return true;
                                else if(isAssignable(tmp[0],type)==1)
                                    return true;
                                else{
                                    String[] type1 = type.split("\\s");
                                    if( type1.length>2 && type1[0].equals("new") && ( type1[1].equals(tmp[0]) || (isAssignable(tmp[0], type1[1])==1) ) )
                                        return true;
                                }
                            }
                        tmp = null;
                    }
                }
                else break;
            }
            else break;
        }
        return false;

    }

    public static String getTypeOfVariable(String myClass, String myMethod, String variable) throws Exception
    {
        //Generator.emit+="\n; getTypeOfVariable class:" + myClass + " ,method:" + myMethod + " ,variable:"+ variable;
        String[] tmp;
        String tmp2 = myClass;
        variable = variable.trim();
        String simpleClass = "";
        String[] tmp_ = tmp2.split("\\s");
        if(tmp_.length>1)
            simpleClass = tmp_[0];
        else
            simpleClass = myClass;
//        System.out.println(tmp2);

        int found = 0;
        String[] tmp5 = variable.split("\\s");
//        if(tmp5.length>2 && tmp5[0].equals("new")) {
//            variable = tmp5[1];
//            System.out.println(tmp5[0]+" "+tmp5[1]+" "+tmp5[2]+" "+variable);
//            if(tmp5[2].equals("[]"))
//                variable += "[]";
//            return variable;
//        }
//        if(myMethod!=null) {
//            tmp = myMethod.split("[.]");
//            tmp2 = tmp[0];
//        }
//        System.out.println(tmp2);
        if(variable.equals("this"))
        {
            String[] c = myClass.split("\\s");
            if(c.length>1) {
                return c[0];
            }
            else {
                return myClass;
            }
        }
        for(int i=0; i<DataVisitor.classes.size();i++)
        {
            if(variable.equals(DataVisitor.classes.get(i)))
                return variable;
        }
        System.out.println(tmp2);
//        System.out.println(DataVisitor.symbolTable);
        List<String> temp3 = new ArrayList<String>(DataVisitor.symbolTable.get(tmp2).values());
        //search the variables declared inside the method
        if(myMethod!=null)
        {
            if(DataVisitor.symbolTable.containsKey(simpleClass+"."+myMethod)) {
                List<String> temp2 = new ArrayList<String>(DataVisitor.symbolTable.get(simpleClass + "." + myMethod).values());
                System.out.println("searching in method");
                System.out.println(temp2);
                for (int k = 0; k < temp2.size(); k++) {
                    tmp = (temp2.get(k)).split("\\s");
                    if (tmp.length > 1)
                        if (variable.equals(tmp[1])) {
                            found++;
                            return tmp[0];
                        }
                    tmp = null;
                }
            }
            else if(DataVisitor.symbolTable.containsKey(myMethod)) {
                List<String> temp2 = new ArrayList<String>(DataVisitor.symbolTable.get(myMethod).values());
                System.out.println("searching in method");
                System.out.println(temp2);
                for (int k = 0; k < temp2.size(); k++) {
                    tmp = (temp2.get(k)).split("\\s");
                    if (tmp.length > 1)
                        if (variable.equals(tmp[1])) {
                            found++;
                            return tmp[0];
                        }
                    tmp = null;
                }
            }
            else if(DataVisitor.symbolTable.containsKey(myClass+"."+myMethod)) {
                List<String> temp2 = new ArrayList<String>(DataVisitor.symbolTable.get(myClass + "." + myMethod).values());
                System.out.println("searching in method");
                System.out.println(temp2);
                for (int k = 0; k < temp2.size(); k++) {
                    tmp = (temp2.get(k)).split("\\s");
                    if (tmp.length > 1)
                        if (variable.equals(tmp[1])) {
                            found++;
                            return tmp[0];
                        }
                    tmp = null;
                }
            }

        }
        //search the variables declared inside the class
        if(found==0)
        {
            System.out.println("searching in class");
            System.out.println(temp3);
            for(int k=0;k<temp3.size();k++)
            {
                tmp = (temp3.get(k)).split("\\s");
                if(tmp.length>1)
                    if (variable.equals(tmp[1])) {
                        found++;
                        return tmp[0];
                    }
                tmp = null;
            }
        }
        //search the variables declared in the parent class if it exists
        int parentIndex = -1;
        if(found==0)
        {
            //tmp = myMethod.split("[.]");
            String[] tmp3 = myClass.split("\\s");
            String[] tmp4;
            if (tmp3.length > 2)    //else there is no parent class
            {
                parentIndex = DataVisitor.classes.indexOf(tmp3[2]);
                if(parentIndex==-1)
                    for(int k = 0; k< DataVisitor.classes.size(); k++)
                    {
                        tmp4 = DataVisitor.classes.get(k).split("\\s");
                        if(tmp4.length>2)
                            if(tmp4[0].equals(tmp3[2]))
                                parentIndex = k;
                    }
                temp3 = new ArrayList<String>(DataVisitor.symbolTable.get(DataVisitor.classes.get(parentIndex)).values());
                for(int k=0;k<temp3.size();k++)
                {
                    tmp = (temp3.get(k)).split("\\s");
                    if(tmp.length>1)
                        if (variable.equals(tmp[1])) {
                            found++;
                            return tmp[0];
                        }
                    tmp = null;
                }
            }
        }
        //search deeper in ancestors
        while(found==0)
        {
            if(parentIndex!=-1)
            {
                String[] tmp3 = DataVisitor.classes.get(parentIndex).split("\\s");
//                            System.out.println(eval.classes.get(parentIndex));
                String[] tmp4;
                if (tmp3.length > 2)    //else there is no parent class
                {
                    parentIndex = DataVisitor.classes.indexOf(tmp3[2]);    //parentOfparent
                    if (parentIndex == -1)
                        for (int k = 0; k < DataVisitor.classes.size(); k++) {
                            tmp4 = DataVisitor.classes.get(k).split("\\s");
                            if (tmp4.length > 2)
                                if (tmp4[0].equals(tmp3[2]))
                                    parentIndex = k;
                        }
                    if (parentIndex == -1)
                        break;
                    temp3 = new ArrayList<String>(DataVisitor.symbolTable.get(DataVisitor.classes.get(parentIndex)).values());
                    for (int k = 0; k < temp3.size(); k++) {
                        tmp = (temp3.get(k)).split("\\s");
                        if(tmp.length>1)
                            if (variable.equals(tmp[1])) {
                                found++;
                                return tmp[0];
                            }
                        tmp = null;
                    }
                }
                else break;
            }
            else break;
        }
        return "";
    }

    public static String getTypeOfMessage(String classname, String message) throws Exception
    {
        //give return type of method call and check for semantic correctness
        String[] s = message.split("[.]");
        String[] tmp;
        String method1[] = s[s.length-1].split("\\(",2);
        if(method1.length<2)
            return "";
        else
            if(!method1[1].endsWith(")"))
                return "";
        String item;
        String cl = null;
        if(s.length<2)
            return "";

        String[] ncl = s[0].split("\\s");
        if(ncl[0].equals("new")&&ncl[2].equals("()"))
            cl=ncl[1];
        List<String> temp3 = null;
        if(cl==null)
            cl = s[0];
        if(DataVisitor.classes.contains(cl)){
            temp3 = new ArrayList<String>(DataVisitor.symbolTable.get(cl).values());
        }
        else{
            for(int i=0; i<DataVisitor.classes.size(); i++)
            {
                String[] temporary = DataVisitor.classes.get(i).split("\\s");
                if(temporary.length>2 && temporary[1].equals("extends") && temporary[0].equals(cl)){
                    cl = DataVisitor.classes.get(i);
                    temp3 = new ArrayList<String>(DataVisitor.symbolTable.get(cl).values());
                }
            }
        }

        if(temp3==null && DataVisitor.classes.contains(classname)) {
//            System.out.println("2nd if");
            temp3 = new ArrayList<String>(DataVisitor.symbolTable.get(classname).values());
            cl = classname;
        }

//        List<String> temp4 = null;
//        if(DataVisitor.parameters.containsKey(cl+"."+method1[0]))
//            temp4 = new ArrayList<String>(DataVisitor.parameters.get(cl+"."+method1[0]).values());
//        else{
//            //search if the parent class has the method, if class doesnt have one
//            String[] n = cl.split("\\s");
//            if(n.length>2 && n[1].equals("extends"))
//            {
//                String parentclass = null;
//                String pc = n[2];
//                while(true){
//                    //first find parent class
//                    if(DataVisitor.classes.contains(pc))
//                        parentclass=pc;
//                    else{
//                        for(int i=0; i<DataVisitor.classes.size(); i++)
//                        {
//                            String[] temporary = DataVisitor.classes.get(i).split("\\s");
//                            if(temporary.length>2 && temporary[1].equals("extends") && temporary[0].equals(pc)){
//                                parentclass = DataVisitor.classes.get(i);
//                                break;
//                            }
//                        }
//                    }
//                    //check if parent class has method
//                    if(DataVisitor.parameters.containsKey(parentclass+"."+method1[0])) {
//                        temp4 = new ArrayList<String>(DataVisitor.parameters.get(parentclass + "." + method1[0]).values());
//                        break;
//                    }
//                    n = parentclass.split("\\s");
//                    if(n.length>2 && n[1].equals("extends"))
//                        pc = n[2];
//                    else
//                        break;
//                }
//            }
//        }
//
//
//        if(temp4==null){
//            System.out.println("No "+s[s.length-1]+ " method");
//            System.out.println(classname +" "+message+" " +Arrays.toString(s));
////            System.exit(1);
//            throw new ParseException("ERROR");
//        }

//        if(method1[1].endsWith(")")){
//            method1[1] = method1[1].substring(0, method1[1].length()-1);
//            String[] myParameters = method1[1].split(",");
//            if(method1[1].equals("")&&(temp4.size()!=0)){
//                System.out.println("ERROR: messageSend: wrong parameters: " + s[1]);
////                System.exit(1);
//                throw new ParseException("ERROR");
//            }else if(!method1[1].equals("")&&myParameters.length!=temp4.size()){
//                System.out.println("ERROR: messageSend: wrong parameters: " + s[1] +" "+ myParameters.length+" "+temp4.size());
////                System.exit(1);
//                throw new ParseException("ERROR");
//            }
//            for(int i=0;i<temp4.size();i++)
//            {
////                System.out.println(myParameters[i] + " "+ temp4.get(i));
//                String[] decParameter = temp4.get(i).split("\\s");
//                if(!(myParameters[i].trim()).equals(decParameter[0].trim())){
//                    System.out.println(decParameter[0].trim()+" "+myParameters[i].trim());
//                    if(isAssignable(decParameter[0].trim(),myParameters[i].trim())!=1){
//                        String[] crop = myParameters[i].trim().split("\\s");
//                        if(crop.length>2 && crop[0].equals("new") && crop[2].equals("()") && (isAssignable(decParameter[0].trim(),crop[1])==1))
//                            continue;
//                        System.out.println("ERROR: messageSend: wrong parameters: " + s[1]);
////                        System.exit(1);
//                        throw new ParseException("ERROR");
//                    }
//                }
//            }
//        }else{
//            System.out.println("ERROR: messageSend: wrong parameters: " + s[1]);
////            System.exit(1);
//            throw new ParseException("ERROR");
//        }
        //
        if(temp3==null)
            return "";
        //find method
        int found = 0;
        item = method1[0];
        //in class
        for (int k = 0; k < temp3.size(); k++) {
            tmp = (temp3.get(k)).split("\\s");
            if (tmp.length > 2 && tmp[0].equals("method"))
                if (item.equals(tmp[2])) {
                    found++;
                    return tmp[1];
                }
            tmp = null;
        }
        //search the methods declared in the parent class if it exists
        int parentIndex = -1;
        String[] tmp4;
        if (found == 0) {
            String[] tmp3 = cl.split("\\s");
            if (tmp3.length > 2)    //else there is no parent class
            {
                parentIndex = DataVisitor.classes.indexOf(tmp3[2]);
                if (parentIndex == -1)
                    for (int k = 0; k < DataVisitor.classes.size(); k++) {
                        tmp4 = DataVisitor.classes.get(k).split("\\s");
                        if (tmp4.length > 2)
                            if (tmp4[0].equals(tmp3[2]))
                                parentIndex = k;
                    }
                temp3 = new ArrayList<String>(DataVisitor.symbolTable.get(DataVisitor.classes.get(parentIndex)).values());
                for (int k = 0; k < temp3.size(); k++) {
                    tmp = (temp3.get(k)).split("\\s");
                    if (tmp.length > 2 && tmp[0].equals("method"))
                        if (item.equals(tmp[2])) {
                            found++;
                            return tmp[1];
                        }
                    tmp = null;
                }
            }
        }
        //search deeper in ancestors
        while (found == 0) {
            if (parentIndex != -1) {
                String[] tmp3 = DataVisitor.classes.get(parentIndex).split("\\s");
//                            System.out.println(eval.classes.get(parentIndex));
                if (tmp3.length > 2)    //else there is no parent class
                {
                    parentIndex = DataVisitor.classes.indexOf(tmp3[2]);    //parentOfparent
                    if (parentIndex == -1)
                        for (int k = 0; k < DataVisitor.classes.size(); k++) {
                            tmp4 = DataVisitor.classes.get(k).split("\\s");
                            if (tmp4.length > 2)
                                if (tmp4[0].equals(tmp3[2]))
                                    parentIndex = k;
                        }
                    if (parentIndex == -1)
                        break;
                    temp3 = new ArrayList<String>(DataVisitor.symbolTable.get(DataVisitor.classes.get(parentIndex)).values());
                    for (int k = 0; k < temp3.size(); k++) {
                        tmp = (temp3.get(k)).split("\\s");
                        if (tmp.length > 2 && tmp[0].equals("method"))
                            if (item.equals(tmp[2])) {
                                found++;
                                return tmp[1];
                            }
                        tmp = null;
                    }
                } else break;
            } else break;
        }
        return "";

    }
}
