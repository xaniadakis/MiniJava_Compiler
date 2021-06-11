#Makefile
MAKEFLAGS = -i
OBJ = $(patsubst %.ll,%.exe,$(wildcard *.ll))
LL = $(wildcard *.ll)

all: $(OBJ)	\
	del

$(OBJ):
	clang -o $@ $(patsubst %.exe,%.ll,$@) > $(patsubst %.exe,%.ERROR,$@) 2>&1
	./$@ > $(patsubst %.exe,%.out,$@)

del:
	grep -lrIZ warning --exclude="Makefile" . | xargs -0 rm -f --

output:
	rm -f *.exe *.ll

clean:
	rm -f *.exe *.ERROR

cleanall:
	rm -f *.exe *.ll *.out *.ERROR
