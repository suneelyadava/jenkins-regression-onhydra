def DATE_TIME = "`date -u +%Y-%m-%d_%H-%M-%S`"
def TRACK = 'dev'
def VXIMG = '/mnt/cl-builds/CumulusLinux-3.7.15_release/build-3.7.15.1007/vx-all-amd64/build/images/'
def VXIMG1 = '/mnt/cl-builds/CumulusLinux-4_release/latest-vx-amd64/build/images/'
def RUNDIR = '$WORKSPACE/run'
def ARTIFACTSDIR='$RUNDIR/test_artifacts'
def FILENAME = '$JOB_NAME-$BUILD_ID-$DATE_TIME.yaml'
def NETQ_ENV_YAML_FILE_PATH = '/tmp/$FILENAME'
def DUT = 'esx-06'
def NETQ_ENV='/mnt/behemoth1/NETQ-JENKINS/Repo/Dev/netq_dev_ova_only_netq_tb3_cloud.yaml'
def LOGLEVEL = 'DEBUG'
def TEST_TYPE = 'netq-daily'
def REPO_PREFIX = '/mnt/behemoth1/NETQ-JENKINS/Repo/Dev/'
def LOADOVA = '$RUNDIR/cl-tests/tests/netq/netq_test_ova_load.py:TestOvaLoadSuite.test_ova_load'
def TESTDIR = '$RUNDIR//cl-tests/tests/netq/netq_sanity_tests.py'
def runtest_opts = '${runtest_opts:-""}'
def UNLOADOVA = '${RUNDIR}/cl-tests/tests/netq/netq_test_ova_load.py:TestOvaLoadSuite.test_ova_unload'
node("hydra-03"){
    def build_ok = true
    stage('Creating ws .....'){
        cleanWs()
        sh script: """git config --global http.sslVerify false
        """
        sh script: """bootstrap.py --user sw-r2d2-bot --gitlab-token 9A7vtjtArb3eoboKo4km --cl-tools-tag master --cl-cafe-tag master --cl-tests-tag master ${RUNDIR};
        """
    }
    stage('Loading OVA....'){
        sh script: """cp $NETQ_ENV /tmp/$FILENAME; \
                export NETQ_ENV_YAML_FILE_PATH=/tmp/$FILENAME; \
                export RUNDIR=$WORKSPACE/run;\
                set -o nounset; set -o errexit; set -o xtrace;\
                mkdir -p ${ARTIFACTSDIR}; \
                . ${RUNDIR}/.venv/bin/activate; \
                ${RUNDIR}/.venv/bin/runtests.sh --no-mgmt-vrf -d $DUT \
                        -t netq-app-load \
            -r $ARTIFACTSDIR/load_summary.txt \
            -T $ARTIFACTSDIR/load_summary.out \
            -x $ARTIFACTSDIR/load_results.xml \
            -j $ARTIFACTSDIR/load_results.json \
            -l $LOGLEVEL \
                        $LOADOVA;
                        """
        archiveArtifacts allowEmptyArchive: true, artifacts: 'run/test_artifacts/**'
        junit allowEmptyResults: true, keepLongStdio: true, testResults: 'run/test_artifacts/results.xml'
    }
    try{
        stage('Running CL3 Agent Sanity ......'){
            sh script: """. $RUNDIR/.venv/bin/activate; \
                export NETQ_ENV_YAML_FILE_PATH=/tmp/$FILENAME; \
                export RUNDIR=$WORKSPACE/run;\
                . ${RUNDIR}/.venv/bin/activate; \
                $RUNDIR/.venv/bin/runtests.sh --no-mgmt-vrf -d $DUT \
            --vx-img \
            -i ${VXIMG} \
            -f $ARTIFACTSDIR \
            -r $ARTIFACTSDIR/summary.txt \
            -T $ARTIFACTSDIR/log.out \
            -x $ARTIFACTSDIR/results.xml \
            -j $ARTIFACTSDIR/results.json \
            -O --tc=options.vm_cl_support:False \
            -l $LOGLEVEL \
            --add-env-to-json $runtest_opts \
            $TESTDIR;\
        """
            archiveArtifacts allowEmptyArchive: true, artifacts: 'run/test_artifacts/**'
            junit allowEmptyResults: true, keepLongStdio: true, testResults: 'run/test_artifacts/results.xml'
        }
    } catch(e){
        build_ok = false
        echo e.toString()
    }
    stage('Unload OVA.....'){
        sh script: """. $RUNDIR/.venv/bin/activate; \
                export NETQ_ENV_YAML_FILE_PATH=/tmp/$FILENAME; \
                export RUNDIR=$WORKSPACE/run;\
                . ${RUNDIR}/.venv/bin/activate; \
                $RUNDIR/.venv/bin/runtests.sh --no-mgmt-vrf -d $DUT \
                            -t netq-app-load \
                -r $ARTIFACTSDIR/unload_summary.txt \
                -T $ARTIFACTSDIR/unload_summary.out \
                -x $ARTIFACTSDIR/unload_results.xml \
                -j $ARTIFACTSDIR/unload_results.json \
                -l $LOGLEVEL \
                            $UNLOADOVA; \
                rm -f /tmp/$FILENAME; \
                """
        archiveArtifacts allowEmptyArchive: true, artifacts: 'run/test_artifacts/**'
        junit allowEmptyResults: true, keepLongStdio: true, testResults: 'run/test_artifacts/results.xml'
    }
    if(build_ok){
        currentBuild.result = "SUCCESS"
    } else {
        currentBuild.result = "FAILURE"
        mail bcc: '', body: "http://sm-telem-04.lab.cumulusnetworks.com:8080/job/netq-agent-sanity/", cc: 'syadava@nvidia.com, anurag@nvidia.com, mrns@nvidia.com, sriharsha@nvidia.com, nagachandra@nvidia.com', from: 'syadava@nvidia.com', replyTo: '', subject: 'Test Results -- Pipeline Agent Sanity-CL-3x is Failed!', to: 'syadava@nvidia.com'
    }
    stage('Email Notification'){
        
    }
    cleanWs cleanWhenFailure: false
}