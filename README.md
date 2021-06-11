# MiniJava Intermediate Code Generator

In this project I had to implement recursive visitors that convert MiniJava code into the intermediate representation used by the LLVM compiler project. The LLVM language is documented in the LLVM Language Reference Manual and can be found here (https://llvm.org/docs/LangRef.html#instruction-reference).

The application can be compiled as follows:

    make clean all

The application can be run as follows:

    java Main [file1] [file2] ... [fileN] , where the [file ] argument can be either a directory or a simple file (in case it's a directory all the java files in it and any subdirectories of it will be translated to .ll files)
    
The .ll files are stored in the same path as their correspondent .java files and can be compiled and executed as follows:

    clang -o file file.ll
    ./file

