// Quorus build and verification on the Jenkins server, modelled on the Qraft pipeline.
//
// Lanes (docs-design/testing/QUORUS_TESTING_README.md section 3):
//   1. Default lane: mvn clean verify, with the JaCoCo gates (60% line coverage per package).
//   2. Host-built runtime jars: docker/build-runtime.sh. Images only package these jars; nothing is
//      compiled inside Docker.
//   3. Full lane: mvn verify '-Dtest.excludedGroups=' without clean, so the docker and slow groups run
//      against the jars from step 2, again with the gates.
//
// SOURCE_TARBALL builds an uncommitted working tree: a .tgz of the tree staged on this server under
// $JENKINS_HOME/quorus-uploads/. Empty (the default) builds the master branch from GitHub.

// Runs one Maven lane, tee-ing its log to logs/. With CONTINUE_PAST_TEST_FAILURES, test failures and
// JaCoCo rule violations no longer stop the reactor; the violations are written to
// logs/coverage-violations-<lane>.txt for flagCoverageViolations.
def mavenLane(String lane, String goals) {
    String lenient = params.CONTINUE_PAST_TEST_FAILURES
            ? '-Dmaven.test.failure.ignore=true -Djacoco.haltOnFailure=false' : ''
    withEnv(["LANE=${lane}", "GOALS=${goals}", "LENIENT=${lenient}"]) {
        sh '''#!/usr/bin/env bash
set -uo pipefail
mkdir -p logs
log="logs/quorus-$LANE-$(date -u +%Y-%m-%d_%H-%M-%S)-$BUILD_NUMBER.log"
# GOALS and LENIENT are word lists, so they are expanded unquoted. Jenkins leaves an empty LENIENT unset.
mvn -B --fail-at-end -Dstyle.color=never -Dmaven.repo.local="$MAVEN_REPO" ${LENIENT:-} $GOALS 2>&1 | tee "$log"
status="${PIPESTATUS[0]}"
grep 'Rule violated' "$log" > "logs/coverage-violations-$LANE.txt" || rm -f -- "logs/coverage-violations-$LANE.txt"
exit "$status"
'''
    }
}

def flagCoverageViolations(String lane) {
    if (fileExists("logs/coverage-violations-${lane}.txt")) {
        unstable("JaCoCo coverage rules violated in the ${lane} lane: logs/coverage-violations-${lane}.txt")
    }
}

// Copies each module's surefire reports and JaCoCo report into reports/<lane>/ and coverage/<lane>/,
// so the two lanes' results stay apart in the JUnit view and the archived artifacts.
def collectReports(String lane) {
    withEnv(["LANE=${lane}"]) {
        sh '''#!/usr/bin/env bash
set -euo pipefail
for dir in */target/surefire-reports; do
    [[ -d "$dir" ]] || continue
    module="${dir%%/*}"
    mkdir -p "reports/$LANE/$module"
    cp -a "$dir/." "reports/$LANE/$module/"
done
for dir in */target/site/jacoco; do
    [[ -d "$dir" ]] || continue
    module="${dir%%/*}"
    mkdir -p "coverage/$LANE/$module"
    cp -a "$dir/." "coverage/$LANE/$module/"
done
'''
    }
}

pipeline {
    agent { label 'linux' }

    options {
        disableConcurrentBuilds()
        skipDefaultCheckout(true)
        timestamps()
        timeout(time: 150, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '10'))
    }

    parameters {
        string(name: 'SOURCE_TARBALL', defaultValue: '', trim: true,
               description: 'Empty builds master from GitHub. Otherwise the file name of a .tgz of a working tree in $JENKINS_HOME/quorus-uploads/ (pre-commit verification).')
        booleanParam(name: 'CONTINUE_PAST_TEST_FAILURES', defaultValue: false,
               description: 'Regression mode: test failures and JaCoCo rule violations do not stop the reactor, so every module runs and reports. Failures still show in the JUnit view, and the build ends UNSTABLE. Off is the release gate.')
        string(name: 'FOCUSED_TESTS', defaultValue: '', trim: true,
               description: 'Empty runs the lanes. Otherwise a Surefire -Dtest selector (comma-separated test classes) run in quorus-core, quorus-controller and quorus-agent with -am, instead of the lanes. Test failures are reported, not fatal, so a TDD red run or a mutation check completes; the build ends UNSTABLE when a test fails.')
        booleanParam(name: 'FOCUSED_INCLUDE_DOCKER', defaultValue: false,
               description: 'With FOCUSED_TESTS: also run selected tests in the docker and slow groups. Builds the host runtime jars first, as the docker tests package them.')
    }

    environment {
        JAVA_HOME = "${WORKSPACE}@tools/sapmachine-jdk-27"
        PATH = "${WORKSPACE}@tools/sapmachine-jdk-27/bin:${env.PATH}"
        MAVEN_REPO = "${WORKSPACE}@repository"
    }

    stages {
        stage('Source') {
            steps {
                deleteDir()
                script {
                    if (params.SOURCE_TARBALL?.trim()) {
                        sh '''#!/usr/bin/env bash
set -euo pipefail
name="$SOURCE_TARBALL"
if [[ ! "$name" =~ ^[A-Za-z0-9._-]+\\.tgz$ ]]; then
    echo "SOURCE_TARBALL must be a plain .tgz file name: $name" >&2
    exit 2
fi
archive="$JENKINS_HOME/quorus-uploads/$name"
[[ -f "$archive" ]] || { echo "No such upload: $archive" >&2; exit 2; }
tar -xzf "$archive" -C "$WORKSPACE"
echo "Source: uploaded working tree $archive ($(sha256sum "$archive" | cut -d' ' -f1))"
[[ -f SOURCE_INFO.txt ]] && cat SOURCE_INFO.txt
'''
                    } else {
                        git url: 'https://github.com/mraysmit/quorus.git', branch: 'master'
                        sh 'git rev-parse HEAD'
                    }
                }
            }
        }

        stage('Toolchain and Docker') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail
if [[ ! -x "$JAVA_HOME/bin/javac" ]]; then
    mkdir -p "$JAVA_HOME"
    archive="${JAVA_HOME}.tar.gz"
    curl --fail --location --retry 3 --connect-timeout 20 --max-time 600 \
        --output "$archive" \
        https://github.com/SAP/SapMachine/releases/download/sapmachine-27/sapmachine-jdk-27_linux-x64_bin.tar.gz
    printf '%s  %s\\n' de400f5991c440d9a454e8d6470cec398ed5cf13277ba890d519d8c661bd8194 "$archive" | sha256sum --check -
    tar -xzf "$archive" -C "$JAVA_HOME" --strip-components=1
    rm -- "$archive"
fi
java --version
javac --version
mvn --version
docker version
docker compose version
'''
            }
        }

        stage('Focused tests') {
            when { expression { params.FOCUSED_TESTS?.trim() } }
            steps {
                sh '''#!/usr/bin/env bash
set -uo pipefail
if [[ ! "$FOCUSED_TESTS" =~ ^[A-Za-z0-9_.,#*$-]+$ ]]; then
    echo "FOCUSED_TESTS must be a Surefire -Dtest selector: $FOCUSED_TESTS" >&2
    exit 2
fi
mkdir -p logs
log="logs/quorus-focused-$(date -u +%Y-%m-%d_%H-%M-%S)-$BUILD_NUMBER.log"
goals=(clean test)
groups=()
if [[ "${FOCUSED_INCLUDE_DOCKER:-false}" == true ]]; then
    # The docker tests package the host-built jars, so build them first and do not clean afterwards.
    MAVEN_ARGS="-Dmaven.repo.local=$MAVEN_REPO -Dstyle.color=never" sh docker/build-runtime.sh 2>&1 | tee "$log"
    [[ "${PIPESTATUS[0]}" == 0 ]] || exit 1
    goals=(test)
    groups=(-Dtest.excludedGroups=)
fi
mvn -B -Dstyle.color=never -Dmaven.repo.local="$MAVEN_REPO" "${goals[@]}" "${groups[@]}" \
    -pl quorus-core,quorus-controller,quorus-agent -am "-Dtest=$FOCUSED_TESTS" \
    -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=true 2>&1 \
    | tee -a "$log"
exit "${PIPESTATUS[0]}"
'''
            }
            post {
                always { collectReports('focused') }
            }
        }

        stage('Default lane and coverage gates') {
            when { expression { !params.FOCUSED_TESTS?.trim() } }
            steps {
                catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                    mavenLane('default', 'clean verify')
                }
            }
            post {
                always {
                    collectReports('default')
                    flagCoverageViolations('default')
                }
            }
        }

        stage('Host-built runtime jars') {
            when { expression { !params.FOCUSED_TESTS?.trim() } }
            steps {
                sh '''#!/usr/bin/env bash
set -uo pipefail
mkdir -p logs
MAVEN_ARGS="-Dmaven.repo.local=$MAVEN_REPO -Dstyle.color=never" sh docker/build-runtime.sh 2>&1 \
    | tee "logs/quorus-runtime-jars-$(date -u +%Y-%m-%d_%H-%M-%S)-$BUILD_NUMBER.log"
exit "${PIPESTATUS[0]}"
'''
            }
        }

        stage('Full lane: docker and slow groups') {
            when { expression { !params.FOCUSED_TESTS?.trim() } }
            steps {
                catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                    sh "find . -path '*/target/surefire-reports' -type d -prune -exec rm -rf -- {} +"
                    mavenLane('full', 'verify -Dtest.excludedGroups=')
                }
            }
            post {
                always {
                    collectReports('full')
                    flagCoverageViolations('full')
                }
            }
        }
    }

    post {
        always {
            junit testResults: 'reports/**/TEST-*.xml', allowEmptyResults: true
            archiveArtifacts artifacts: 'logs/**, reports/**, coverage/**, **/hs_err_pid*.log, **/replay_pid*.log',
                             allowEmptyArchive: true
        }
    }
}
