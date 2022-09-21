def pipeline_timeout = 10
def JENKINS_SCRIPTS_BRANCH = 'parallel-mtr'
def JENKINS_SCRIPTS_REPO = 'https://github.com/kamil-holubicki/jenkins-pipelines'
def WORKER_1_ABORTED = false
def WORKER_2_ABORTED = false
def WORKER_3_ABORTED = false
def WORKER_4_ABORTED = false
def WORKER_5_ABORTED = false
def WORKER_6_ABORTED = false
def WORKER_7_ABORTED = false
def WORKER_8_ABORTED = false
def BUILD_NUMBER_BINARIES_FOR_RERUN = 0
def LABEL = 'docker-32gb'

if (
    (params.ANALYZER_OPTS.contains('-DWITH_ASAN=ON')) ||
    (params.ANALYZER_OPTS.contains('-DWITH_UBSAN=ON'))
    ) { pipeline_timeout = 48 }

if (params.ANALYZER_OPTS.contains('-DWITH_VALGRIND=ON'))
    { pipeline_timeout = 144 }

pipeline {
    parameters {
        string(
            defaultValue: '',
            description: 'Reuse PXC, PXB24, PXB80 binaries built in the specified build. Useful for quick MTR test rerun without rebuild.',
            name: 'BUILD_NUMBER_BINARIES',
            trim: true)
        string(
            defaultValue: 'https://github.com/percona/percona-xtradb-cluster',
            description: 'URL to PXC repository',
            name: 'GIT_REPO',
            trim: true)
        string(
            defaultValue: '8.0',
            description: 'Tag/PR/Branch for PXC repository',
            name: 'BRANCH',
            trim: true)
        booleanParam(
            defaultValue: false,
            description: 'Check only if you pass PR number to BRANCH field',
            name: 'USE_PR')
        booleanParam(
            defaultValue: true,
            description: 'If checked, the PXB80_BRANCH will be ignored and latest available version will be used',
            name: 'PXB80_LATEST')
        string(
            defaultValue: 'https://github.com/percona/percona-xtrabackup',
            description: 'URL to PXB80 repository',
            name: 'PXB80_REPO',
            trim: true)
        string(
            defaultValue: 'percona-xtrabackup-8.0.27-19',
            description: 'Tag/Branch for PXB80 repository',
            name: 'PXB80_BRANCH',
            trim: true)
        booleanParam(
            defaultValue: true,
            description: 'If checked, the PXB24_BRANCH will be ignored and latest available version will be used',
            name: 'PXB24_LATEST')
        string(
            defaultValue: 'https://github.com/percona/percona-xtrabackup',
            description: 'URL to PXB24 repository',
            name: 'PXB24_REPO',
            trim: true)
        string(
            defaultValue: 'percona-xtrabackup-2.4.24',
            description: 'Tag/Branch for PXC repository',
            name: 'PXB24_BRANCH',
            trim: true)
        choice(
            choices: '/usr/bin/cmake',
            description: 'path to cmake binary',
            name: 'JOB_CMAKE')
        choice(
            choices: 'centos:7\ncentos:8\nubuntu:bionic\nubuntu:focal\nubuntu:hirsute\ndebian:buster\ndebian:bullseye',
            description: 'OS version for compilation',
            name: 'DOCKER_OS')
        choice(
            choices: 'RelWithDebInfo\nDebug',
            description: 'Type of build to produce',
            name: 'CMAKE_BUILD_TYPE')
        string(
            defaultValue: '',
            description: 'cmake options',
            name: 'CMAKE_OPTS')
        string(
            defaultValue: '',
            description: 'make options, like VERBOSE=1',
            name: 'MAKE_OPTS')
        choice(
            choices: '\n-DWITH_ASAN=ON -DWITH_ASAN_SCOPE=ON\n-DWITH_ASAN=ON\n-DWITH_ASAN=ON -DWITH_ASAN_SCOPE=ON -DWITH_UBSAN=ON\n-DWITH_ASAN=ON -DWITH_UBSAN=ON\n-DWITH_UBSAN=ON\n-DWITH_MSAN=ON\n-DWITH_VALGRIND=ON',
            description: 'Enable code checking',
            name: 'ANALYZER_OPTS')
        string(
            defaultValue: '--unit-tests-report --big-test --mem',
            description: 'mysql-test-run.pl options, for options like: --big-test --only-big-test --nounit-tests --unit-tests-report',
            name: 'MTR_ARGS')
        choice(
            choices: 'yes\nno',
            description: 'Run case-insensetive MTR tests',
            name: 'CI_FS_MTR')
        string(
            defaultValue: '2',
            description: 'mtr can start n parallel server and distrbute workload among them. More parallelism is better but extra parallelism (beyond CPU power) will have less effect. This value is used for the Galera specific test suites.',
            name: 'GALERA_PARALLEL_RUN')
        choice(
            choices: 'yes\nno\ngalera_only',
            description: 'yes - full MTR\nno - run mtr suites based on variables WORKER_N_MTR_SUITES\ngalera_only - only Galera related suites (incl. wsrep and sys_var)',
            name: 'FULL_MTR')
        string(
            defaultValue: '',
            description: 'Suites to be ran on worker 1 when FULL_MTR is no. Unit tests, if requested, can be ran here only!',
            name: 'WORKER_1_MTR_SUITES')
        string(
            defaultValue: '',
            description: 'Suites to be ran on worker 2 when FULL_MTR is no',
            name: 'WORKER_2_MTR_SUITES')
        string(
            defaultValue: '',
            description: 'Suites to be ran on worker 3 when FULL_MTR is no',
            name: 'WORKER_3_MTR_SUITES')
        string(
            defaultValue: '',
            description: 'Suites to be ran on worker 4 when FULL_MTR is no',
            name: 'WORKER_4_MTR_SUITES')
        string(
            defaultValue: '',
            description: 'Suites to be ran on worker 5 when FULL_MTR is no',
            name: 'WORKER_5_MTR_SUITES')
        string(
            defaultValue: '',
            description: 'Suites to be ran on worker 6 when FULL_MTR is no',
            name: 'WORKER_6_MTR_SUITES')
        string(
            defaultValue: '',
            description: 'Suites to be ran on worker 7 when FULL_MTR is no',
            name: 'WORKER_7_MTR_SUITES')
        string(
            defaultValue: '',
            description: 'Suites to be ran on worker 8 when FULL_MTR is no',
            name: 'WORKER_8_MTR_SUITES')
        booleanParam(
            defaultValue: true,
            description: 'Rerun aborted workers',
            name: 'ALLOW_ABORTED_WORKERS_RERUN')
    }
    agent {
        label 'micro-amazon'
    }
    options {
        skipDefaultCheckout()
        skipStagesAfterUnstable()
        timeout(time: 6, unit: 'DAYS')
        buildDiscarder(logRotator(numToKeepStr: '200', artifactNumToKeepStr: '200'))
        copyArtifactPermission('pxc-8.0-param-parallel-mtr');
    }
    stages {
        stage('Prepare') {
            steps {
                script {
                    currentBuild.displayName = "${BUILD_NUMBER} ${CMAKE_BUILD_TYPE}/${DOCKER_OS}"
                }

                sh 'echo Prepare: \$(date -u "+%s")'
                echo 'Checking PXC branch version'
                sh '''
                    MY_BRANCH_BASE_MAJOR=8
                    MY_BRANCH_BASE_MINOR=0

                    if [ -f /usr/bin/apt ]; then
                        sudo apt-get update
                    fi

                    if [[ ${USE_PR} == "true" ]]; then
                        if [ -f /usr/bin/yum ]; then
                            sudo yum -y install jq
                        else
                            sudo apt-get install -y jq
                        fi

                        GIT_REPO=$(curl https://api.github.com/repos/percona/percona-xtradb-cluster/pulls/${BRANCH} | jq -r '.head.repo.html_url')
                        BRANCH=$(curl https://api.github.com/repos/percona/percona-xtradb-cluster/pulls/${BRANCH} | jq -r '.head.ref')
                    fi

                    RAW_VERSION_LINK=$(echo ${GIT_REPO%.git} | sed -e "s:github.com:raw.githubusercontent.com:g")
                    REPLY=$(curl -Is ${RAW_VERSION_LINK}/${BRANCH}/MYSQL_VERSION | head -n 1 | awk '{print $2}')
                    if [[ ${REPLY} != 200 ]]; then
                        wget ${RAW_VERSION_LINK}/${BRANCH}/VERSION -O ${WORKSPACE}/VERSION-${BUILD_NUMBER}
                    else
                        wget ${RAW_VERSION_LINK}/${BRANCH}/MYSQL_VERSION -O ${WORKSPACE}/VERSION-${BUILD_NUMBER}
                    fi
                    source ${WORKSPACE}/VERSION-${BUILD_NUMBER}
                    if [[ ${MYSQL_VERSION_MAJOR} -lt ${MY_BRANCH_BASE_MAJOR} ]] ; then
                        echo "Are you trying to build wrong branch?"
                        echo "You are trying to build ${MYSQL_VERSION_MAJOR}.${MYSQL_VERSION_MINOR} instead of ${MY_BRANCH_BASE_MAJOR}.${MY_BRANCH_BASE_MINOR}!"
                        rm -f ${WORKSPACE}/VERSION-${BUILD_NUMBER}
                        exit 1
                    fi
                    rm -f ${WORKSPACE}/VERSION-${BUILD_NUMBER}



                    if [[ "${FULL_MTR}" == "yes" ]]; then
                        # Try to get suites split from pxc repo. If not present, fallback to hardcoded.
                        REPLY=$(curl -Is ${RAW_VERSION_LINK}/${BRANCH}/mysql-test/suites-groups.sh | head -n 1 | awk '{print $2}')
                        if [[ ${REPLY} != 200 ]]; then
                            # Unit tests will be executed by worker 1, so do not assign galera suites, wich are executed
                            # with less parallelism
                            WORKER_1_MTR_SUITES=innodb_undo,test_services,audit_null,service_sys_var_registration,connection_control,data_masking,binlog_57_decryption,service_udf_registration,service_status_var_registration,procfs,interactive_utilities,percona-pam-for-mysql
                            WORKER_2_MTR_SUITES=galera_nbo,galera_3nodes,galera_sr,galera_3nodes_nbo,galera_3nodes_sr,wsrep
                            WORKER_3_MTR_SUITES=engines/funcs,innodb
                            WORKER_4_MTR_SUITES=main,rpl
                            WORKER_5_MTR_SUITES=rpl_nogtid,rpl_gtid
                            WORKER_6_MTR_SUITES=parts,group_replication,clone,innodb_gis
                            WORKER_7_MTR_SUITES=stress,perfschema,component_keyring_file,binlog,innodb_fts,sys_vars,innodb_zip,x,gcol,engines/iuds,encryption,federated,funcs_1,auth_sec,binlog_nogtid,binlog_gtid,funcs_2,jp,information_schema,rpl_encryption,sysschema,json,opt_trace,audit_log,collations,gis,query_rewrite_plugins,test_service_sql_api,secondary_engine
                            WORKER_8_MTR_SUITES=galera
                        else
                            wget ${RAW_VERSION_LINK}/${BRANCH}/mysql-test/suites-groups.sh -O ${WORKSPACE}/suites-groups.sh

                            # Check if splitted suites contain all suites
                            wget ${RAW_VERSION_LINK}/${BRANCH}/mysql-test/mysql-test-run.pl -O ${WORKSPACE}/mysql-test-run.pl
                            chmod +x ${WORKSPACE}/suites-groups.sh
                            ${WORKSPACE}/suites-groups.sh check ${WORKSPACE}/mysql-test-run.pl

                            # Source suites split
                            source ${WORKSPACE}/suites-groups.sh
                        fi

                        echo ${WORKER_1_MTR_SUITES} > ${WORKSPACE}/worker_1.suites
                        echo ${WORKER_2_MTR_SUITES} > ${WORKSPACE}/worker_2.suites
                        echo ${WORKER_3_MTR_SUITES} > ${WORKSPACE}/worker_3.suites
                        echo ${WORKER_4_MTR_SUITES} > ${WORKSPACE}/worker_4.suites
                        echo ${WORKER_5_MTR_SUITES} > ${WORKSPACE}/worker_5.suites
                        echo ${WORKER_6_MTR_SUITES} > ${WORKSPACE}/worker_6.suites
                        echo ${WORKER_7_MTR_SUITES} > ${WORKSPACE}/worker_7.suites
                        echo ${WORKER_8_MTR_SUITES} > ${WORKSPACE}/worker_8.suites
                    fi
                '''
                script {
                    if (env.FULL_MTR == 'yes') {
                        env.WORKER_1_MTR_SUITES = sh(returnStdout: true, script: "cat ${WORKSPACE}/worker_1.suites").trim()
                        env.WORKER_2_MTR_SUITES = sh(returnStdout: true, script: "cat ${WORKSPACE}/worker_2.suites").trim()
                        env.WORKER_3_MTR_SUITES = sh(returnStdout: true, script: "cat ${WORKSPACE}/worker_3.suites").trim()
                        env.WORKER_4_MTR_SUITES = sh(returnStdout: true, script: "cat ${WORKSPACE}/worker_4.suites").trim()
                        env.WORKER_5_MTR_SUITES = sh(returnStdout: true, script: "cat ${WORKSPACE}/worker_5.suites").trim()
                        env.WORKER_6_MTR_SUITES = sh(returnStdout: true, script: "cat ${WORKSPACE}/worker_6.suites").trim()
                        env.WORKER_7_MTR_SUITES = sh(returnStdout: true, script: "cat ${WORKSPACE}/worker_7.suites").trim()
                        env.WORKER_8_MTR_SUITES = sh(returnStdout: true, script: "cat ${WORKSPACE}/worker_8.suites").trim()
                    } else if (env.FULL_MTR == 'galera_only') {
                        env.WORKER_1_MTR_SUITES = "wsrep,sys_vars"
                        env.WORKER_2_MTR_SUITES = "galera_nbo"
                        env.WORKER_3_MTR_SUITES = "galera_3nodes"
                        env.WORKER_4_MTR_SUITES = "galera_sr"
                        env.WORKER_5_MTR_SUITES = "galera_3nodes_nbo"
                        env.WORKER_6_MTR_SUITES = "galera_3nodes_sr"
                        env.WORKER_7_MTR_SUITES = "galera|nobig"
                        env.WORKER_8_MTR_SUITES = "galera|big"
                    }

                    echo "WORKER_1_MTR_SUITES: ${env.WORKER_1_MTR_SUITES}"
                    echo "WORKER_2_MTR_SUITES: ${env.WORKER_2_MTR_SUITES}"
                    echo "WORKER_3_MTR_SUITES: ${env.WORKER_3_MTR_SUITES}"
                    echo "WORKER_4_MTR_SUITES: ${env.WORKER_4_MTR_SUITES}"
                    echo "WORKER_5_MTR_SUITES: ${env.WORKER_5_MTR_SUITES}"
                    echo "WORKER_6_MTR_SUITES: ${env.WORKER_6_MTR_SUITES}"
                    echo "WORKER_7_MTR_SUITES: ${env.WORKER_7_MTR_SUITES}"
                    echo "WORKER_8_MTR_SUITES: ${env.WORKER_8_MTR_SUITES}"

                    env.BUILD_TAG_BINARIES = "jenkins-${env.JOB_NAME}-${env.BUILD_NUMBER_BINARIES}"
                    BUILD_NUMBER_BINARIES_FOR_RERUN = env.BUILD_NUMBER_BINARIES
                    sh 'printenv'
                }
            }
        }
        stage('Check out and Build PXB/PXC') {
            when {
                beforeAgent true
                expression { env.BUILD_NUMBER_BINARIES == '' }
            }
            parallel {
                stage('Build PXC80') {
                    agent { label LABEL }
                    steps {
                        script {
	                        echo "JENKINS_SCRIPTS_BRANCH: $JENKINS_SCRIPTS_BRANCH"
	                        echo "JENKINS_SCRIPTS_REPO: $JENKINS_SCRIPTS_REPO"
       	                    sh '''
		                        which git
	                        '''
                        }
                        git branch: JENKINS_SCRIPTS_BRANCH, url: JENKINS_SCRIPTS_REPO
                        echo 'Checkout PXC80 sources'
                        sh '''
                            # sudo is needed for better node recovery after compilation failure
                            # if building failed on compilation stage directory will have files owned by docker user
                            sudo git reset --hard
                            sudo git clean -xdf
                            sudo rm -rf sources
                            ./pxc/local/checkout PXC80
                        '''

                        echo 'Build PXC80'
                        withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c42456e5-c28d-4962-b32c-b75d161bff27', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                            sh '''
                                aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                sg docker -c "
                                    if [ \$(docker ps -q | wc -l) -ne 0 ]; then
                                        docker ps -q | xargs docker stop --time 1 || :
                                    fi
                                    ./pxc/docker/run-build-pxc-parallel-mtr ${DOCKER_OS}
                                " 2>&1 | tee build.log

                                if [[ -f \$(ls pxc/sources/pxc/results/*.tar.gz | head -1) ]]; then
                                    until aws s3 cp --no-progress --acl public-read pxc/sources/pxc/results/*.tar.gz s3://pxc-build-cache/${BUILD_TAG}/pxc80.tar.gz; do
                                        sleep 5
                                    done
                                else
                                    echo cannot find compiled archive
                                    exit 1
                                fi
                            '''
                        }
                        script {
                            env.BUILD_TAG_BINARIES = env.BUILD_TAG
                            BUILD_NUMBER_BINARIES_FOR_RERUN = env.BUILD_NUMBER
                        }
                    }
                }
                stage('Build PXB24') {
                    agent { label 'docker' }
                    steps {
                        script {
	                        echo "JENKINS_SCRIPTS_BRANCH: $JENKINS_SCRIPTS_BRANCH"
	                        echo "JENKINS_SCRIPTS_REPO: $JENKINS_SCRIPTS_REPO"
       	                    sh '''
		                        which git
	                        '''
                        }
                        git branch: JENKINS_SCRIPTS_BRANCH, url: JENKINS_SCRIPTS_REPO
                        echo 'Checkout PXB24 sources'
                        sh '''
                            # sudo is needed for better node recovery after compilation failure
                            # if building failed on compilation stage directory will have files owned by docker user
                            sudo git reset --hard
                            sudo git clean -xdf
                            sudo rm -rf sources
                            ./pxc/local/checkout PXB24
                        '''
                        echo 'Build PXB24'
                        withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c42456e5-c28d-4962-b32c-b75d161bff27', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                            sh '''
                                aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                sg docker -c "
                                    if [ \$(docker ps -q | wc -l) -ne 0 ]; then
                                        docker ps -q | xargs docker stop --time 1 || :
                                    fi
                                    ./pxc/docker/run-build-pxb24 ${DOCKER_OS}
                                " 2>&1 | tee build.log

                                if [[ -f \$(ls pxc/sources/pxb24/results/*.tar.gz | head -1) ]]; then
                                    until aws s3 cp --no-progress --acl public-read pxc/sources/pxb24/results/*.tar.gz s3://pxc-build-cache/${BUILD_TAG}/pxb24.tar.gz; do
                                        sleep 5
                                    done
                                else
                                    echo cannot find compiled archive
                                    exit 1
                                fi
                            '''
                        }
                    }
                }
                stage('Build PXB80') {
                    agent { label LABEL }
                    steps {
                        script {
	                        echo "JENKINS_SCRIPTS_BRANCH: $JENKINS_SCRIPTS_BRANCH"
	                        echo "JENKINS_SCRIPTS_REPO: $JENKINS_SCRIPTS_REPO"
       	                    sh '''
		                        which git
	                        '''
                        }
                        git branch: JENKINS_SCRIPTS_BRANCH, url: JENKINS_SCRIPTS_REPO
                        echo 'Checkout PXB80 sources'
                        sh '''
                            # sudo is needed for better node recovery after compilation failure
                            # if building failed on compilation stage directory will have files owned by docker user
                            sudo git reset --hard
                            sudo git clean -xdf
                            sudo rm -rf sources
                            ./pxc/local/checkout PXB80
                        '''
                        echo 'Build PXB80'
                        withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c42456e5-c28d-4962-b32c-b75d161bff27', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                            sh '''
                                aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                sg docker -c "
                                    if [ \$(docker ps -q | wc -l) -ne 0 ]; then
                                        docker ps -q | xargs docker stop --time 1 || :
                                    fi
                                    ./pxc/docker/run-build-pxb80 ${DOCKER_OS}
                                " 2>&1 | tee build.log

                                if [[ -f \$(ls pxc/sources/pxb80/results/*.tar.gz | head -1) ]]; then
                                    until aws s3 cp --no-progress --acl public-read pxc/sources/pxb80/results/*.tar.gz s3://pxc-build-cache/${BUILD_TAG}/pxb80.tar.gz; do
                                        sleep 5
                                    done
                                else
                                    echo cannot find compiled archive
                                    exit 1
                                fi
                            '''
                       }
                    }
                }
            }
        }
        stage('Test') {
            parallel {
                stage('Test - 1') {
                    when {
                        beforeAgent true
                        expression { (env.WORKER_1_MTR_SUITES?.trim()) }
                    }
                    agent { label LABEL }
                    steps {
                        catchError(buildResult: 'UNSTABLE') {
                            script {
                                WORKER_1_ABORTED = true
                                echo "WORKER_1_ABORTED = true"
                            }
                            timeout(time: pipeline_timeout, unit: 'HOURS')  {
                                script {
                                    echo "JENKINS_SCRIPTS_BRANCH: $JENKINS_SCRIPTS_BRANCH"
                                    echo "JENKINS_SCRIPTS_REPO: $JENKINS_SCRIPTS_REPO"
                                    sh '''
                                        which git
                                    '''
                                }
                                git branch: JENKINS_SCRIPTS_BRANCH, url: JENKINS_SCRIPTS_REPO
                                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c42456e5-c28d-4962-b32c-b75d161bff27', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                                    sh '''
                                        sudo git reset --hard
                                        sudo git clean -xdf
                                        rm -rf pxc/sources/* || :
                                        sudo git -C sources reset --hard || :
                                        sudo git -C sources clean -xdf   || :

                                        until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG_BINARIES}/pxb24.tar.gz ./pxc/sources/pxc/results/pxb24/pxb24.tar.gz; do
                                            sleep 5
                                        done

                                        until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG_BINARIES}/pxb80.tar.gz ./pxc/sources/pxc/results/pxb80/pxb80.tar.gz; do
                                            sleep 5
                                        done

                                        until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG_BINARIES}/pxc80.tar.gz ./pxc/sources/pxc/results/pxc80.tar.gz; do
                                            sleep 5
                                        done

                                        # Allow unit tests execution only on 1st worker if requested
                                        # Allow case insensitive FS tests only on 1st worker if requested
                                        # Allow CI FS tests only on 1st worker

                                        if [[ \$CI_FS_MTR == 'yes' ]]; then
                                            if [[ ! -f /mnt/ci_disk_\$CMAKE_BUILD_TYPE.img ]] && [[ -z \$(mount | grep /mnt/ci_disk_dir_\$CMAKE_BUILD_TYPE) ]]; then
                                                sudo dd if=/dev/zero of=/mnt/ci_disk_\$CMAKE_BUILD_TYPE.img bs=1G count=10
                                                sudo /sbin/mkfs.vfat /mnt/ci_disk_\$CMAKE_BUILD_TYPE.img
                                                sudo mkdir -p /mnt/ci_disk_dir_\$CMAKE_BUILD_TYPE
                                                sudo mount -o loop -o uid=27 -o gid=27 -o check=r /mnt/ci_disk_\$CMAKE_BUILD_TYPE.img /mnt/ci_disk_dir_\$CMAKE_BUILD_TYPE
                                            fi
                                        fi
                                        export MTR_SUITES=${WORKER_1_MTR_SUITES}

                                        aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                        sg docker -c "
                                            if [ \$(docker ps -q | wc -l) -ne 0 ]; then
                                                docker ps -q | xargs docker stop --time 1 || :
                                            fi
                                            ./pxc/docker/run-test-parallel-mtr ${DOCKER_OS} 1
                                        "
                                    '''
                                }  // withCredentials
                            }  // timeout
                            step([$class: 'JUnitResultArchiver', testResults: 'pxc/sources/pxc/results/*.xml', healthScaleFactor: 1.0])
                            archiveArtifacts 'pxc/sources/pxc/results/*.xml,pxc/sources/pxc/results/pxc80-test-mtr_logs-*.tar.gz'
                            script {
                                WORKER_1_ABORTED = false
                                echo "WORKER_1_ABORTED = false"
                            }
                        } // catch
                    }
                }
                stage('Test - 2') {
                    when {
                        beforeAgent true
                        expression { (env.WORKER_2_MTR_SUITES?.trim()) }
                    }
                    agent { label LABEL }
                    steps {
                        catchError(buildResult: 'UNSTABLE') {
                            script {
                                WORKER_2_ABORTED = true
                                echo "WORKER_2_ABORTED = true"
                            }
                            timeout(time: pipeline_timeout, unit: 'HOURS')  {
                                script {
                                    echo "JENKINS_SCRIPTS_BRANCH: $JENKINS_SCRIPTS_BRANCH"
                                    echo "JENKINS_SCRIPTS_REPO: $JENKINS_SCRIPTS_REPO"
                                    sh '''
                                        which git
                                    '''
                                }
                                git branch: JENKINS_SCRIPTS_BRANCH, url: JENKINS_SCRIPTS_REPO
                                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c42456e5-c28d-4962-b32c-b75d161bff27', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                                    sh '''
                                        sudo git reset --hard
                                        sudo git clean -xdf
                                        rm -rf pxc/sources/* || :
                                        sudo git -C sources reset --hard || :
                                        sudo git -C sources clean -xdf   || :

                                        until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG_BINARIES}/pxb24.tar.gz ./pxc/sources/pxc/results/pxb24/pxb24.tar.gz; do
                                            sleep 5
                                        done

                                        until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG_BINARIES}/pxb80.tar.gz ./pxc/sources/pxc/results/pxb80/pxb80.tar.gz; do
                                            sleep 5
                                        done

                                        until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG_BINARIES}/pxc80.tar.gz ./pxc/sources/pxc/results/pxc80.tar.gz; do
                                            sleep 5
                                        done

                                        export MTR_SUITES=${WORKER_2_MTR_SUITES}
                                        MTR_ARGS=${MTR_ARGS//"--unit-tests-report"/""}
                                        CI_FS_MTR=no

                                        aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                        sg docker -c "
                                            if [ \$(docker ps -q | wc -l) -ne 0 ]; then
                                                docker ps -q | xargs docker stop --time 1 || :
                                            fi
                                            ./pxc/docker/run-test-parallel-mtr ${DOCKER_OS} 2
                                        "
                                    '''
                                }  // withCredentials
                            }  // timeout
                            step([$class: 'JUnitResultArchiver', testResults: 'pxc/sources/pxc/results/*.xml', healthScaleFactor: 1.0])
                            archiveArtifacts 'pxc/sources/pxc/results/*.xml,pxc/sources/pxc/results/pxc80-test-mtr_logs-*.tar.gz'
                            script {
                                WORKER_2_ABORTED = false
                                echo "WORKER_2_ABORTED = false"
                            }
                        } // catch
                    }
                }
                stage('Test - 3') {
                    when {
                        beforeAgent true
                        expression { (env.WORKER_3_MTR_SUITES?.trim()) }
                    }
                    agent { label LABEL }
                    steps {
                        catchError(buildResult: 'UNSTABLE') {
                            script {
                                WORKER_3_ABORTED = true
                                echo "WORKER_3_ABORTED = true"
                            }
                            timeout(time: pipeline_timeout, unit: 'HOURS')  {
                                script {
                                    echo "JENKINS_SCRIPTS_BRANCH: $JENKINS_SCRIPTS_BRANCH"
                                    echo "JENKINS_SCRIPTS_REPO: $JENKINS_SCRIPTS_REPO"
                                    sh '''
                                        which git
                                    '''
                                }
                                git branch: JENKINS_SCRIPTS_BRANCH, url: JENKINS_SCRIPTS_REPO
                                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c42456e5-c28d-4962-b32c-b75d161bff27', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                                    sh '''
                                        sudo git reset --hard
                                        sudo git clean -xdf
                                        rm -rf pxc/sources/* || :
                                        sudo git -C sources reset --hard || :
                                        sudo git -C sources clean -xdf   || :

                                        until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG_BINARIES}/pxb24.tar.gz ./pxc/sources/pxc/results/pxb24/pxb24.tar.gz; do
                                            sleep 5
                                        done

                                        until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG_BINARIES}/pxb80.tar.gz ./pxc/sources/pxc/results/pxb80/pxb80.tar.gz; do
                                            sleep 5
                                        done

                                        until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG_BINARIES}/pxc80.tar.gz ./pxc/sources/pxc/results/pxc80.tar.gz; do
                                            sleep 5
                                        done

                                        export MTR_SUITES=${WORKER_3_MTR_SUITES}
                                        MTR_ARGS=${MTR_ARGS//"--unit-tests-report"/""}
                                        CI_FS_MTR=no

                                        aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                        sg docker -c "
                                            if [ \$(docker ps -q | wc -l) -ne 0 ]; then
                                                docker ps -q | xargs docker stop --time 1 || :
                                            fi
                                            ./pxc/docker/run-test-parallel-mtr ${DOCKER_OS} 3
                                        "
                                    '''
                                }  // withCredentials
                            }  // timeout
                            step([$class: 'JUnitResultArchiver', testResults: 'pxc/sources/pxc/results/*.xml', healthScaleFactor: 1.0])
                            archiveArtifacts 'pxc/sources/pxc/results/*.xml,pxc/sources/pxc/results/pxc80-test-mtr_logs-*.tar.gz'
                            script {
                                WORKER_3_ABORTED = false
                                echo "WORKER_3_ABORTED = false"
                            }
                        } // catch
                    }
                }
                stage('Test - 4') {
                    when {
                        beforeAgent true
                        expression { (env.WORKER_4_MTR_SUITES?.trim()) }
                    }
                    agent { label LABEL }
                    steps {
                        catchError(buildResult: 'UNSTABLE') {
                            script {
                                WORKER_4_ABORTED = true
                                echo "WORKER_4_ABORTED = true"
                            }
                            timeout(time: pipeline_timeout, unit: 'HOURS')  {
                                script {
                                    echo "JENKINS_SCRIPTS_BRANCH: $JENKINS_SCRIPTS_BRANCH"
                                    echo "JENKINS_SCRIPTS_REPO: $JENKINS_SCRIPTS_REPO"
                                    sh '''
                                        which git
                                    '''
                                }
                                git branch: JENKINS_SCRIPTS_BRANCH, url: JENKINS_SCRIPTS_REPO
                                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c42456e5-c28d-4962-b32c-b75d161bff27', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                                    sh '''
                                        sudo git reset --hard
                                        sudo git clean -xdf
                                        rm -rf pxc/sources/* || :
                                        sudo git -C sources reset --hard || :
                                        sudo git -C sources clean -xdf   || :

                                        until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG_BINARIES}/pxb24.tar.gz ./pxc/sources/pxc/results/pxb24/pxb24.tar.gz; do
                                            sleep 5
                                        done

                                        until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG_BINARIES}/pxb80.tar.gz ./pxc/sources/pxc/results/pxb80/pxb80.tar.gz; do
                                            sleep 5
                                        done

                                        until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG_BINARIES}/pxc80.tar.gz ./pxc/sources/pxc/results/pxc80.tar.gz; do
                                            sleep 5
                                        done

                                        export MTR_SUITES=${WORKER_4_MTR_SUITES}
                                        MTR_ARGS=${MTR_ARGS//"--unit-tests-report"/""}
                                        CI_FS_MTR=no

                                        aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                        sg docker -c "
                                            if [ \$(docker ps -q | wc -l) -ne 0 ]; then
                                                docker ps -q | xargs docker stop --time 1 || :
                                            fi
                                            ./pxc/docker/run-test-parallel-mtr ${DOCKER_OS} 4
                                        "
                                    '''
                                }  // withCredentials
                            }  // timeout
                            step([$class: 'JUnitResultArchiver', testResults: 'pxc/sources/pxc/results/*.xml', healthScaleFactor: 1.0])
                            archiveArtifacts 'pxc/sources/pxc/results/*.xml,pxc/sources/pxc/results/pxc80-test-mtr_logs-*.tar.gz'
                            script {
                                WORKER_4_ABORTED = false
                                echo "WORKER_4_ABORTED = false"
                            }
                        } // catch
                    }
                }
                stage('Test - 5') {
                    when {
                        beforeAgent true
                        expression { (env.WORKER_5_MTR_SUITES?.trim()) }
                    }
                    agent { label LABEL }
                    steps {
                        catchError(buildResult: 'UNSTABLE') {
                            script {
                                WORKER_5_ABORTED = true
                                echo "WORKER_5_ABORTED = true"
                            }
                            timeout(time: pipeline_timeout, unit: 'HOURS')  {
                                script {
                                    echo "JENKINS_SCRIPTS_BRANCH: $JENKINS_SCRIPTS_BRANCH"
                                    echo "JENKINS_SCRIPTS_REPO: $JENKINS_SCRIPTS_REPO"
                                    sh '''
                                        which git
                                    '''
                                }
                                git branch: JENKINS_SCRIPTS_BRANCH, url: JENKINS_SCRIPTS_REPO
                                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c42456e5-c28d-4962-b32c-b75d161bff27', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                                    sh '''
                                        sudo git reset --hard
                                        sudo git clean -xdf
                                        rm -rf pxc/sources/* || :
                                        sudo git -C sources reset --hard || :
                                        sudo git -C sources clean -xdf   || :

                                        until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG_BINARIES}/pxb24.tar.gz ./pxc/sources/pxc/results/pxb24/pxb24.tar.gz; do
                                            sleep 5
                                        done

                                        until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG_BINARIES}/pxb80.tar.gz ./pxc/sources/pxc/results/pxb80/pxb80.tar.gz; do
                                            sleep 5
                                        done

                                        until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG_BINARIES}/pxc80.tar.gz ./pxc/sources/pxc/results/pxc80.tar.gz; do
                                            sleep 5
                                        done

                                        export MTR_SUITES=${WORKER_5_MTR_SUITES}
                                        MTR_ARGS=${MTR_ARGS//"--unit-tests-report"/""}
                                        CI_FS_MTR=no

                                        aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                        sg docker -c "
                                            if [ \$(docker ps -q | wc -l) -ne 0 ]; then
                                                docker ps -q | xargs docker stop --time 1 || :
                                            fi
                                            ./pxc/docker/run-test-parallel-mtr ${DOCKER_OS} 5
                                        "
                                    '''
                                }  // withCredentials
                            }  // timeout
                            step([$class: 'JUnitResultArchiver', testResults: 'pxc/sources/pxc/results/*.xml', healthScaleFactor: 1.0])
                            archiveArtifacts 'pxc/sources/pxc/results/*.xml,pxc/sources/pxc/results/pxc80-test-mtr_logs-*.tar.gz'
                            script {
                                WORKER_5_ABORTED = false
                                echo "WORKER_5_ABORTED = false"
                            }
                        } // catch
                    }
                }
                stage('Test - 6') {
                    when {
                        beforeAgent true
                        expression { (env.WORKER_6_MTR_SUITES?.trim()) }
                    }
                    agent { label LABEL }
                    steps {
                        catchError(buildResult: 'UNSTABLE') {
                            script {
                                WORKER_6_ABORTED = true
                                echo "WORKER_6_ABORTED = true"
                            }
                            timeout(time: pipeline_timeout, unit: 'HOURS')  {
                                script {
                                    echo "JENKINS_SCRIPTS_BRANCH: $JENKINS_SCRIPTS_BRANCH"
                                    echo "JENKINS_SCRIPTS_REPO: $JENKINS_SCRIPTS_REPO"
                                    sh '''
                                        which git
                                    '''
                                }
                                git branch: JENKINS_SCRIPTS_BRANCH, url: JENKINS_SCRIPTS_REPO
                                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c42456e5-c28d-4962-b32c-b75d161bff27', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                                    sh '''
                                        sudo git reset --hard
                                        sudo git clean -xdf
                                        rm -rf pxc/sources/* || :
                                        sudo git -C sources reset --hard || :
                                        sudo git -C sources clean -xdf   || :

                                        until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG_BINARIES}/pxb24.tar.gz ./pxc/sources/pxc/results/pxb24/pxb24.tar.gz; do
                                            sleep 5
                                        done

                                        until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG_BINARIES}/pxb80.tar.gz ./pxc/sources/pxc/results/pxb80/pxb80.tar.gz; do
                                            sleep 5
                                        done

                                        until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG_BINARIES}/pxc80.tar.gz ./pxc/sources/pxc/results/pxc80.tar.gz; do
                                            sleep 5
                                        done

                                        export MTR_SUITES=${WORKER_6_MTR_SUITES}
                                        MTR_ARGS=${MTR_ARGS//"--unit-tests-report"/""}
                                        CI_FS_MTR=no

                                        aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                        sg docker -c "
                                            if [ \$(docker ps -q | wc -l) -ne 0 ]; then
                                                docker ps -q | xargs docker stop --time 1 || :
                                            fi
                                            ./pxc/docker/run-test-parallel-mtr ${DOCKER_OS} 6
                                        "
                                    '''
                                }  // withCredentials
                            }  // timeout
                            step([$class: 'JUnitResultArchiver', testResults: 'pxc/sources/pxc/results/*.xml', healthScaleFactor: 1.0])
                            archiveArtifacts 'pxc/sources/pxc/results/*.xml,pxc/sources/pxc/results/pxc80-test-mtr_logs-*.tar.gz'
                            script {
                                WORKER_6_ABORTED = false
                                echo "WORKER_6_ABORTED = false"
                            }
                        } // catch
                    }
                }
                stage('Test - 7') {
                    when {
                        beforeAgent true
                        expression { (env.WORKER_7_MTR_SUITES?.trim()) }
                    }
                    agent { label LABEL }
                    steps {
                        catchError(buildResult: 'UNSTABLE') {
                            script {
                                WORKER_7_ABORTED = true
                                echo "WORKER_7_ABORTED = true"
                            }
                            timeout(time: pipeline_timeout, unit: 'HOURS')  {
                                script {
                                    echo "JENKINS_SCRIPTS_BRANCH: $JENKINS_SCRIPTS_BRANCH"
                                    echo "JENKINS_SCRIPTS_REPO: $JENKINS_SCRIPTS_REPO"
                                    sh '''
                                        which git
                                    '''
                                }
                                git branch: JENKINS_SCRIPTS_BRANCH, url: JENKINS_SCRIPTS_REPO
                                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c42456e5-c28d-4962-b32c-b75d161bff27', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                                    sh '''
                                        sudo git reset --hard
                                        sudo git clean -xdf
                                        rm -rf pxc/sources/* || :
                                        sudo git -C sources reset --hard || :
                                        sudo git -C sources clean -xdf   || :

                                        until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG_BINARIES}/pxb24.tar.gz ./pxc/sources/pxc/results/pxb24/pxb24.tar.gz; do
                                            sleep 5
                                        done

                                        until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG_BINARIES}/pxb80.tar.gz ./pxc/sources/pxc/results/pxb80/pxb80.tar.gz; do
                                            sleep 5
                                        done

                                        until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG_BINARIES}/pxc80.tar.gz ./pxc/sources/pxc/results/pxc80.tar.gz; do
                                            sleep 5
                                        done

                                        export MTR_SUITES=${WORKER_7_MTR_SUITES}
                                        MTR_ARGS=${MTR_ARGS//"--unit-tests-report"/""}
                                        CI_FS_MTR=no

                                        aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                        sg docker -c "
                                            if [ \$(docker ps -q | wc -l) -ne 0 ]; then
                                                docker ps -q | xargs docker stop --time 1 || :
                                            fi
                                            ./pxc/docker/run-test-parallel-mtr ${DOCKER_OS} 7
                                        "
                                    '''
                                }  // withCredentials
                            }  // timeout
                            step([$class: 'JUnitResultArchiver', testResults: 'pxc/sources/pxc/results/*.xml', healthScaleFactor: 1.0])
                            archiveArtifacts 'pxc/sources/pxc/results/*.xml,pxc/sources/pxc/results/pxc80-test-mtr_logs-*.tar.gz'
                            script {
                                WORKER_7_ABORTED = false
                                echo "WORKER_7_ABORTED = false"
                            }
                        } // catch
                    }
                }
                stage('Test - 8') {
                    when {
                        beforeAgent true
                        expression { (env.WORKER_8_MTR_SUITES?.trim()) }
                    }
                    agent { label LABEL }
                    steps {
                        catchError(buildResult: 'UNSTABLE') {
                            script {
                                WORKER_8_ABORTED = true
                                echo "WORKER_8_ABORTED = true"
                            }
                            timeout(time: pipeline_timeout, unit: 'HOURS')  {
                                script {
                                    echo "JENKINS_SCRIPTS_BRANCH: $JENKINS_SCRIPTS_BRANCH"
                                    echo "JENKINS_SCRIPTS_REPO: $JENKINS_SCRIPTS_REPO"
                                    sh '''
                                        which git
                                    '''
                                }
                                git branch: JENKINS_SCRIPTS_BRANCH, url: JENKINS_SCRIPTS_REPO
                                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c42456e5-c28d-4962-b32c-b75d161bff27', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                                    sh '''
                                        sudo git reset --hard
                                        sudo git clean -xdf
                                        rm -rf pxc/sources/* || :
                                        sudo git -C sources reset --hard || :
                                        sudo git -C sources clean -xdf   || :

                                        until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG_BINARIES}/pxb24.tar.gz ./pxc/sources/pxc/results/pxb24/pxb24.tar.gz; do
                                            sleep 5
                                        done

                                        until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG_BINARIES}/pxb80.tar.gz ./pxc/sources/pxc/results/pxb80/pxb80.tar.gz; do
                                            sleep 5
                                        done

                                        until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG_BINARIES}/pxc80.tar.gz ./pxc/sources/pxc/results/pxc80.tar.gz; do
                                            sleep 5
                                        done

                                        export MTR_SUITES=${WORKER_8_MTR_SUITES}
                                        MTR_ARGS=${MTR_ARGS//"--unit-tests-report"/""}
                                        CI_FS_MTR=no

                                        aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                        sg docker -c "
                                            if [ \$(docker ps -q | wc -l) -ne 0 ]; then
                                                docker ps -q | xargs docker stop --time 1 || :
                                            fi
                                            ./pxc/docker/run-test-parallel-mtr ${DOCKER_OS} 8
                                        "
                                    '''
                                }  // withCredentials
                            }  // timeout
                            step([$class: 'JUnitResultArchiver', testResults: 'pxc/sources/pxc/results/*.xml', healthScaleFactor: 1.0])
                            archiveArtifacts 'pxc/sources/pxc/results/*.xml,pxc/sources/pxc/results/pxc80-test-mtr_logs-*.tar.gz'
                            script {
                                WORKER_8_ABORTED = false
                                echo "WORKER_8_ABORTED = false"
                            }
                        } // catch
                    }
                }
            }
        }
    }
    post {
        always {
            script {
                if (env.ALLOW_ABORTED_WORKERS_RERUN == 'true') {
                    echo "allow aborted reruns ${env.ALLOW_ABORTED_WORKERS_RERUN}"
                    echo "WORKER_1_ABORTED: $WORKER_1_ABORTED"
                    def rerunNeeded = false
                    def WORKER_1_RERUN_SUITES = ""
                    def WORKER_2_RERUN_SUITES = ""
                    def WORKER_3_RERUN_SUITES = ""
                    def WORKER_4_RERUN_SUITES = ""
                    def WORKER_5_RERUN_SUITES = ""
                    def WORKER_6_RERUN_SUITES = ""
                    def WORKER_7_RERUN_SUITES = ""
                    def WORKER_8_RERUN_SUITES = ""

                    if (WORKER_1_ABORTED) {
                        echo "rerun worker 1"
                        WORKER_1_RERUN_SUITES = env.WORKER_1_MTR_SUITES
                        rerunNeeded = true
                    }
                    if (WORKER_2_ABORTED) {
                        echo "rerun worker 2"
                        WORKER_2_RERUN_SUITES = env.WORKER_2_MTR_SUITES
                        rerunNeeded = true
                    }
                    if (WORKER_3_ABORTED) {
                        echo "rerun worker 3"
                        WORKER_3_RERUN_SUITES = env.WORKER_3_MTR_SUITES
                        rerunNeeded = true
                    }
                    if (WORKER_4_ABORTED) {
                        echo "rerun worker 4"
                        WORKER_4_RERUN_SUITES = env.WORKER_4_MTR_SUITES
                        rerunNeeded = true
                    }
                    if (WORKER_5_ABORTED) {
                        echo "rerun worker 5"
                        WORKER_5_RERUN_SUITES = env.WORKER_5_MTR_SUITES
                        rerunNeeded = true
                    }
                    if (WORKER_6_ABORTED) {
                        echo "rerun worker 6"
                        WORKER_6_RERUN_SUITES = env.WORKER_6_MTR_SUITES
                        rerunNeeded = true
                    }
                    if (WORKER_7_ABORTED) {
                        echo "rerun worker 7"
                        WORKER_7_RERUN_SUITES = env.WORKER_7_MTR_SUITES
                        rerunNeeded = true
                    }
                    if (WORKER_8_ABORTED) {
                        echo "rerun worker 8"
                        WORKER_8_RERUN_SUITES = env.WORKER_8_MTR_SUITES
                        rerunNeeded = true
                    }
                    echo "rerun needed: $rerunNeeded"
                    if (rerunNeeded) {
                        echo "restarting aborted workers"
                        build job: 'pxc-8.0-pipeline-parallel-mtr',
                        wait: false,
                        parameters: [
                            string(name:'BUILD_NUMBER_BINARIES', value: BUILD_NUMBER_BINARIES_FOR_RERUN),
                            string(name:'GIT_REPO', value: env.GIT_REPO),
                            string(name:'BRANCH', value: env.BRANCH),
                            string(name:'DOCKER_OS', value: env.DOCKER_OS),
                            string(name:'JOB_CMAKE', value: env.JOB_CMAKE),
                            string(name:'CMAKE_BUILD_TYPE', value: env.CMAKE_BUILD_TYPE),
                            string(name:'ANALYZER_OPTS', value: env.ANALYZER_OPTS),
                            string(name:'CMAKE_OPTS', value: env.CMAKE_OPTS),
                            string(name:'MAKE_OPTS', value: env.MAKE_OPTS),
                            string(name:'MTR_ARGS', value: env.MTR_ARGS),
                            string(name:'CI_FS_MTR', value: env.CI_FS_MTR),
                            string(name:'GALERA_PARALLEL_RUN', value: env.GALERA_PARALLEL_RUN),
                            string(name:'FULL_MTR', value:'no'),
                            string(name:'WORKER_1_MTR_SUITES', value: WORKER_1_RERUN_SUITES),
                            string(name:'WORKER_2_MTR_SUITES', value: WORKER_2_RERUN_SUITES),
                            string(name:'WORKER_3_MTR_SUITES', value: WORKER_3_RERUN_SUITES),
                            string(name:'WORKER_4_MTR_SUITES', value: WORKER_4_RERUN_SUITES),
                            string(name:'WORKER_5_MTR_SUITES', value: WORKER_5_RERUN_SUITES),
                            string(name:'WORKER_6_MTR_SUITES', value: WORKER_6_RERUN_SUITES),
                            string(name:'WORKER_7_MTR_SUITES', value: WORKER_7_RERUN_SUITES),
                            string(name:'WORKER_8_MTR_SUITES', value: WORKER_8_RERUN_SUITES),
                            booleanParam(name: 'ALLOW_ABORTED_WORKERS_RERUN', value: false),
                            string(name:'BUILD_DISPLAY_NAME', value: "${BUILD_NUMBER} retry")
                        ]
                    }
                }  // env.ALLOW_ABORTED_WORKERS_RERUN
            }
            sh '''
                echo Finish: \$(date -u "+%s")
            '''
        }
    }
}
