all: mini	\
	main

mini:
	java -jar ../jtb132di.jar -te minijava.jj  0>/dev/null 1>/dev/null 2>/dev/null
	java -jar ../javacc5.jar minijava-jtb.jj  0>/dev/null 1>/dev/null 2>/dev/null

main:
	javac Compiler.java #0>/dev/null 1>/dev/null 2>/dev/null
	javac Helper.java #0>/dev/null 1>/dev/null 2>/dev/null
	javac Main.java #0>/dev/null 1>/dev/null 2>/dev/null
ifdef file
	java Main $(file)
endif

run:
	java Main $(file)

clean:
	rm -f *.class *~

cleanall:
	rm -f ./syntaxtree/*.class ./visitor/*.class  *.class *~

