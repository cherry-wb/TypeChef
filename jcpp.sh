#!/bin/bash -e
#!/bin/bash -vxe
if [ -z "$jcppConfLoaded" ]; then
  source jcpp.conf
fi

# What you should configure
javaOpts='$javaOpts -Xmx2G -Xms128m'

macro_stats_path=macroDebug.txt
debugsource_path=debugsource.txt

# For Java compiled stuff!
basePath=.

#mainClass="org.anarres.cpp.Main"
mainClass="de.fosd.typechef.linux.LinuxPreprocessorFrontend"

# Brute argument parsing
# The right thing to do would be to be a gcc replacement, parse its flags and
# select the ones we care about.
if [ $# -lt 1 ]; then
  echo "Not enough arguments!" >&2
  exit 1
fi
inp=$1
shift

. setupOutPaths.sh.inc

#time scala -cp BoaCaseStudy/target/scala_2.8.1/classes:FeatureExprLib/lib/org.sat4j.core.jar:FeatureExprLib/target/scala_2.8.1/classes:\
#  PartialPreprocessor/target/scala_2.8.1/classes:PartialPreprocessor/lib/gnu.getopt.jar \
#  <(echo -e '#define b ciao\nb')

if [ ! -f "$outPreproc" ]; then
  echo "=="
  echo "==Preprocess source"
  echo "=="
  gcc -Wp,-P -U __weak $gccOpts -E "$inp" "$@" > "$outPreproc" || true
fi

# Beware: the embedded for loop requotes the passed argument. That's dark magic,
# don't ever try to touch it. It simplifies your life as a user of this program
# though!
echo "==Partially preprocessing $inp"

bash -c "time java -ea $javaOpts -cp \
$basePath/project/boot/scala-2.8.1/lib/scala-library.jar:\
$basePath/PartialPreprocessor/lib/gnu.getopt.jar:\
$basePath/PartialPreprocessor/lib/junit.jar:\
$basePath/org.sat4j.core/target/scala_2.8.1/classes:\
$basePath/FeatureExprLib/target/scala_2.8.1/classes:\
$basePath/PartialPreprocessor/target/scala_2.8.1/classes:\
$basePath/ParserFramework/target/scala_2.8.1/classes:\
$basePath/CParser/target/scala_2.8.1/classes:\
$basePath/CTypeChecker/target/scala_2.8.1/classes:\
$basePath/LinuxAnalysis/target/scala_2.8.1/classes:\
$basePath/PreprocessorFrontend/target/scala_2.8.1/classes \
  $mainClass \
  $(for arg in $partialPreprocFlags "$@"; do echo -n "\"$arg\" "; done) \
  '$inp' -o '$outPartialPreproc' 2> '$outErr' |tee '$outDbg'" \
  2> "$outTime" || true
#bash -c "time java -ea $javaOpts -jar $sbtPath 'project PreprocessorFrontend' \
#  \"run $(for arg in $partialPreprocFlags "$@"; do echo -n "\"$arg\" "; done) \
#  '$inp' -o '$outPartialPreproc'\"  \
#  2> '$outErr'|tee '$outDbg'" \
#  2> "$outTime" || true


cat "$outErr" 1>&2