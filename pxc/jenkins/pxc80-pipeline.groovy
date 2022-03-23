pipeline_timeout = 10

pipeline {
    parameters {
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
            choices: 'centos:7\ncentos:8\nubuntu:bionic\nubuntu:focal\ndebian:buster',
            description: 'OS version for compilation',
            name: 'DOCKER_OS')
        choice(
            choices: '/usr/bin/cmake',
            description: 'path to cmake binary',
            name: 'JOB_CMAKE')
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
            choices: 'no\nyes',
            description: 'Build with ASAN',
            name: 'WITH_ASAN')
        string(
            defaultValue: '8',
            description: 'mtr can start n parallel server and distrbute workload among them. More parallelism is better but extra parallelism (beyond CPU power) will have less effect. This value is used for all test suites except Galera specific suites.',
            name: 'PARALLEL_RUN')
        string(
        	defaultValue: '2',
        	description: 'mtr can start n parallel server and distrbute workload among them. More parallelism is better but extra parallelism (beyond CPU power) will have less effect. This value is used for the Galera specific test suites.',
        	name: 'GALERA_PARALLEL_RUN')
        choice(
            choices: 'yes\nno',
            description: 'Run mtr suites based on variable MTR_SUITES if the value is `no`. Otherwise the full mtr will be perfomed.',
            name: 'FULL_MTR')
        string(
            defaultValue: '',
            description: 'Suites to be ran on worker 1 when FULL_MTR is no',
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
        string(
            defaultValue: '--unit-tests-report --big-test --mem',
            description: 'mysql-test-run.pl options, for options like: --big-test --only-big-test --nounit-tests --unit-tests-report',
            name: 'MTR_ARGS')
    }
    agent {
        label 'micro-amazon'
    }
    options {
        skipDefaultCheckout()
        skipStagesAfterUnstable()
        timeout(time: 6, unit: 'DAYS')
        buildDiscarder(logRotator(numToKeepStr: '200', artifactNumToKeepStr: '200'))
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
                '''
                sh '''
                if [[ "${FULL_MTR}" == "yes" ]]; then
                    WORKER_1_MTR_SUITES=galera,galera_nbo,galera_3nodes,galera_sr,galera_3nodes_nbo,galera_3nodes_sr,wsrep
                    WORKER_2_MTR_SUITES=innodb_undo,test_services,audit_null,service_sys_var_registration,connection_control,data_masking,binlog_57_decryption,service_udf_registration,service_status_var_registration,procfs,interactive_utilities,percona-pam-for-mysql
                    WORKER_3_MTR_SUITES=engines/funcs,innodb
                    WORKER_4_MTR_SUITES=main,rpl
                    WORKER_5_MTR_SUITES=rpl_nogtid,rpl_gtid
                    WORKER_6_MTR_SUITES=parts,group_replication,clone,innodb_gis
                    WORKER_7_MTR_SUITES=stress,perfschema,component_keyring_file,binlog,innodb_fts,sys_vars,innodb_zip,x,gcol,engines/iuds,encryption,federated,funcs_1,auth_sec,binlog_nogtid,binlog_gtid,funcs_2,jp,information_schema,rpl_encryption,sysschema,json,opt_trace,audit_log,collations,gis,query_rewrite_plugins,test_service_sql_api,secondary_engine
                    WORKER_8_MTR_SUITES=

                    echo ${WORKER_1_MTR_SUITES} > ../worker_1.suites
                    echo ${WORKER_2_MTR_SUITES} > ../worker_2.suites
                    echo ${WORKER_3_MTR_SUITES} > ../worker_3.suites
                    echo ${WORKER_4_MTR_SUITES} > ../worker_4.suites
                    echo ${WORKER_5_MTR_SUITES} > ../worker_5.suites
                    echo ${WORKER_6_MTR_SUITES} > ../worker_6.suites
                    echo ${WORKER_7_MTR_SUITES} > ../worker_7.suites
                    echo ${WORKER_8_MTR_SUITES} > ../worker_8.suites
                fi
                '''
                script {
                    if (env.FULL_MTR == 'yes') {
                        env.WORKER_1_MTR_SUITES = sh(returnStdout: true, script: "cat ../worker_1.suites").trim()
                        env.WORKER_2_MTR_SUITES = sh(returnStdout: true, script: "cat ../worker_2.suites").trim()
                        env.WORKER_3_MTR_SUITES = sh(returnStdout: true, script: "cat ../worker_3.suites").trim()
                        env.WORKER_4_MTR_SUITES = sh(returnStdout: true, script: "cat ../worker_4.suites").trim()
                        env.WORKER_5_MTR_SUITES = sh(returnStdout: true, script: "cat ../worker_5.suites").trim()
                        env.WORKER_6_MTR_SUITES = sh(returnStdout: true, script: "cat ../worker_6.suites").trim()
                        env.WORKER_7_MTR_SUITES = sh(returnStdout: true, script: "cat ../worker_7.suites").trim()
                        env.WORKER_8_MTR_SUITES = sh(returnStdout: true, script: "cat ../worker_8.suites").trim()
                    }                    
                    echo "WORKER_1_MTR_SUITES: ${env.WORKER_1_MTR_SUITES}"
                    echo "WORKER_2_MTR_SUITES: ${env.WORKER_2_MTR_SUITES}"
                    echo "WORKER_3_MTR_SUITES: ${env.WORKER_3_MTR_SUITES}"
                    echo "WORKER_4_MTR_SUITES: ${env.WORKER_4_MTR_SUITES}"
                    echo "WORKER_5_MTR_SUITES: ${env.WORKER_5_MTR_SUITES}"
                    echo "WORKER_6_MTR_SUITES: ${env.WORKER_6_MTR_SUITES}"
                    echo "WORKER_7_MTR_SUITES: ${env.WORKER_7_MTR_SUITES}"
                    echo "WORKER_8_MTR_SUITES: ${env.WORKER_8_MTR_SUITES}"
                    sh 'printenv'
                }
            }
        }
        stage('Check out and Build PXB') {
            when { 
                beforeAgent true
                expression { "1" == "1" }
            }

            parallel {
                stage('Build PXC80') {
                        agent { label 'docker-32gb' }
                        steps {
                            git branch: 'parallel-mtr', url: 'https://github.com/kamil-holubicki/jenkins-pipelines'
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
                                        ./pxc/docker/run-build-pxc ${DOCKER_OS}
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
                        }
                }
                stage('Build PXB24') {
                    agent { label 'docker' }
                    steps {
                        git branch: 'parallel-mtr', url: 'https://github.com/kamil-holubicki/jenkins-pipelines'
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
                    agent { label 'docker-32gb' }
                    steps {
                        git branch: 'parallel-mtr', url: 'https://github.com/kamil-holubicki/jenkins-pipelines'
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
        stage('Test PXC80') {
            parallel {
                stage('Test PXC80 - 1') {
                        when { 
                            beforeAgent true
                            expression { (env.WORKER_1_MTR_SUITES?.trim()) }
                        }
                        agent { label 'docker-32gb' }
                        steps {
                            git branch: 'parallel-mtr', url: 'https://github.com/kamil-holubicki/jenkins-pipelines'
                            echo 'Test PXC80'
                            echo "WORKER_1_MTR_SUITES: ${env.WORKER_1_MTR_SUITES}"
                            withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c42456e5-c28d-4962-b32c-b75d161bff27', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                                sh '''
                                    echo "WORKER_1_MTR_SUITES: $WORKER_1_MTR_SUITES"

                                    sudo git reset --hard
                                    sudo git clean -xdf
                                    rm -rf pxc/sources/* || :
                                    sudo git -C sources reset --hard || :
                                    sudo git -C sources clean -xdf   || :

                                    until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG}/pxb24.tar.gz ./pxc/sources/pxc/results/pxb24/pxb24.tar.gz; do
                                        sleep 5
                                    done

                                    until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG}/pxb80.tar.gz ./pxc/sources/pxc/results/pxb80/pxb80.tar.gz; do
                                        sleep 5
                                    done

                                    until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG}/pxc80.tar.gz ./pxc/sources/pxc/results/pxc80.tar.gz; do
                                        sleep 5
                                    done

                                    export MTR_SUITES=${WORKER_1_MTR_SUITES}
                                    aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                    sg docker -c "
                                        if [ \$(docker ps -q | wc -l) -ne 0 ]; then
                                            docker ps -q | xargs docker stop --time 1 || :
                                        fi
                                        ./pxc/docker/run-test ${DOCKER_OS} 1
                                    "
                                '''
                            }
                            step([$class: 'JUnitResultArchiver', testResults: 'pxc/sources/pxc/results/*.xml', healthScaleFactor: 1.0])
                            archiveArtifacts 'pxc/sources/pxc/results/*.xml,pxc/sources/pxc/results/pxc80-test-mtr_logs.tar.gz'
                        }
                }
                stage('Test PXC80 - 2') {
                        when { 
                            beforeAgent true
                            expression { (env.WORKER_2_MTR_SUITES?.trim()) }
                        }
                        agent { label 'docker-32gb' }
                        steps {
                            git branch: 'parallel-mtr', url: 'https://github.com/kamil-holubicki/jenkins-pipelines'
                            echo 'Test PXC80'
                            withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c42456e5-c28d-4962-b32c-b75d161bff27', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                                sh '''
                                    echo "WORKER_2_MTR_SUITES: ${WORKER_2_MTR_SUITES}"

                                    sudo git reset --hard
                                    sudo git clean -xdf
                                    rm -rf pxc/sources/* || :
                                    sudo git -C sources reset --hard || :
                                    sudo git -C sources clean -xdf   || :

                                    until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG}/pxb24.tar.gz ./pxc/sources/pxc/results/pxb24/pxb24.tar.gz; do
                                        sleep 5
                                    done

                                    until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG}/pxb80.tar.gz ./pxc/sources/pxc/results/pxb80/pxb80.tar.gz; do
                                        sleep 5
                                    done

                                    until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG}/pxc80.tar.gz ./pxc/sources/pxc/results/pxc80.tar.gz; do
                                        sleep 5
                                    done

                                    export MTR_SUITES=${WORKER_2_MTR_SUITES}
                                    aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                    sg docker -c "
                                        if [ \$(docker ps -q | wc -l) -ne 0 ]; then
                                            docker ps -q | xargs docker stop --time 1 || :
                                        fi
                                        ./pxc/docker/run-test ${DOCKER_OS} 2
                                    "
                                '''
                            }
                            step([$class: 'JUnitResultArchiver', testResults: 'pxc/sources/pxc/results/*.xml', healthScaleFactor: 1.0])
                            archiveArtifacts 'pxc/sources/pxc/results/*.xml,pxc/sources/pxc/results/pxc80-test-mtr_logs.tar.gz'
                        }
                }
                stage('Test PXC80 - 3') {
                        when { 
                            beforeAgent true
                            expression { (env.WORKER_3_MTR_SUITES?.trim()) }
                        }
                        agent { label 'docker-32gb' }
                        steps {
                            git branch: 'parallel-mtr', url: 'https://github.com/kamil-holubicki/jenkins-pipelines'
                            echo 'Test PXC80'
                            withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c42456e5-c28d-4962-b32c-b75d161bff27', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                                sh '''
                                    echo "WORKER_3_MTR_SUITES: ${WORKER_3_MTR_SUITES}"

                                    sudo git reset --hard
                                    sudo git clean -xdf
                                    rm -rf pxc/sources/* || :
                                    sudo git -C sources reset --hard || :
                                    sudo git -C sources clean -xdf   || :

                                    until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG}/pxb24.tar.gz ./pxc/sources/pxc/results/pxb24/pxb24.tar.gz; do
                                        sleep 5
                                    done

                                    until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG}/pxb80.tar.gz ./pxc/sources/pxc/results/pxb80/pxb80.tar.gz; do
                                        sleep 5
                                    done

                                    until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG}/pxc80.tar.gz ./pxc/sources/pxc/results/pxc80.tar.gz; do
                                        sleep 5
                                    done

                                    export MTR_SUITES=${WORKER_3_MTR_SUITES}
                                    aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                    sg docker -c "
                                        if [ \$(docker ps -q | wc -l) -ne 0 ]; then
                                            docker ps -q | xargs docker stop --time 1 || :
                                        fi
                                        ./pxc/docker/run-test ${DOCKER_OS} 3
                                    "
                                '''
                            }
                            step([$class: 'JUnitResultArchiver', testResults: 'pxc/sources/pxc/results/*.xml', healthScaleFactor: 1.0])
                            archiveArtifacts 'pxc/sources/pxc/results/*.xml,pxc/sources/pxc/results/pxc80-test-mtr_logs.tar.gz'
                        }
                }
                stage('Test PXC80 - 4') {
                        when { 
                            beforeAgent true
                            expression { (env.WORKER_4_MTR_SUITES?.trim()) }
                        }
                        agent { label 'docker-32gb' }
                        steps {
                            git branch: 'parallel-mtr', url: 'https://github.com/kamil-holubicki/jenkins-pipelines'
                            echo 'Test PXC80'
                            withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c42456e5-c28d-4962-b32c-b75d161bff27', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                                sh '''
                                    echo "WORKER_4_MTR_SUITES: ${WORKER_4_MTR_SUITES}"

                                    sudo git reset --hard
                                    sudo git clean -xdf
                                    rm -rf pxc/sources/* || :
                                    sudo git -C sources reset --hard || :
                                    sudo git -C sources clean -xdf   || :

                                    until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG}/pxb24.tar.gz ./pxc/sources/pxc/results/pxb24/pxb24.tar.gz; do
                                        sleep 5
                                    done

                                    until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG}/pxb80.tar.gz ./pxc/sources/pxc/results/pxb80/pxb80.tar.gz; do
                                        sleep 5
                                    done

                                    until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG}/pxc80.tar.gz ./pxc/sources/pxc/results/pxc80.tar.gz; do
                                        sleep 5
                                    done

                                    export MTR_SUITES=${WORKER_4_MTR_SUITES}
                                    aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                    sg docker -c "
                                        if [ \$(docker ps -q | wc -l) -ne 0 ]; then
                                            docker ps -q | xargs docker stop --time 1 || :
                                        fi
                                        ./pxc/docker/run-test ${DOCKER_OS} 4
                                    "
                                '''
                            }
                            step([$class: 'JUnitResultArchiver', testResults: 'pxc/sources/pxc/results/*.xml', healthScaleFactor: 1.0])
                            archiveArtifacts 'pxc/sources/pxc/results/*.xml,pxc/sources/pxc/results/pxc80-test-mtr_logs.tar.gz'
                        }
                }
                stage('Test PXC80 - 5') {
                        when { 
                            beforeAgent true
                            expression { (env.WORKER_5_MTR_SUITES?.trim()) }
                        }
                        agent { label 'docker-32gb' }
                        steps {
                            git branch: 'parallel-mtr', url: 'https://github.com/kamil-holubicki/jenkins-pipelines'
                            echo 'Test PXC80'
                            withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c42456e5-c28d-4962-b32c-b75d161bff27', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                                sh '''
                                    echo "WORKER_5_MTR_SUITES: ${WORKER_5_MTR_SUITES}"

                                    sudo git reset --hard
                                    sudo git clean -xdf
                                    rm -rf pxc/sources/* || :
                                    sudo git -C sources reset --hard || :
                                    sudo git -C sources clean -xdf   || :

                                    until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG}/pxb24.tar.gz ./pxc/sources/pxc/results/pxb24/pxb24.tar.gz; do
                                        sleep 5
                                    done

                                    until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG}/pxb80.tar.gz ./pxc/sources/pxc/results/pxb80/pxb80.tar.gz; do
                                        sleep 5
                                    done

                                    until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG}/pxc80.tar.gz ./pxc/sources/pxc/results/pxc80.tar.gz; do
                                        sleep 5
                                    done

                                    export MTR_SUITES=${WORKER_5_MTR_SUITES}
                                    aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                    sg docker -c "
                                        if [ \$(docker ps -q | wc -l) -ne 0 ]; then
                                            docker ps -q | xargs docker stop --time 1 || :
                                        fi
                                        ./pxc/docker/run-test ${DOCKER_OS} 5
                                    "
                                '''
                            }
                            step([$class: 'JUnitResultArchiver', testResults: 'pxc/sources/pxc/results/*.xml', healthScaleFactor: 1.0])
                            archiveArtifacts 'pxc/sources/pxc/results/*.xml,pxc/sources/pxc/results/pxc80-test-mtr_logs.tar.gz'
                        }
                }
                stage('Test PXC80 - 6') {
                        when { 
                            beforeAgent true
                            expression { (env.WORKER_6_MTR_SUITES?.trim()) }
                        }
                        agent { label 'docker-32gb' }
                        steps {
                            git branch: 'parallel-mtr', url: 'https://github.com/kamil-holubicki/jenkins-pipelines'
                            echo 'Test PXC80'
                            withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c42456e5-c28d-4962-b32c-b75d161bff27', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                                sh '''
                                    echo "WORKER_6_MTR_SUITES: ${WORKER_6_MTR_SUITES}"
                                    
                                    sudo git reset --hard
                                    sudo git clean -xdf
                                    rm -rf pxc/sources/* || :
                                    sudo git -C sources reset --hard || :
                                    sudo git -C sources clean -xdf   || :

                                    until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG}/pxb24.tar.gz ./pxc/sources/pxc/results/pxb24/pxb24.tar.gz; do
                                        sleep 5
                                    done

                                    until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG}/pxb80.tar.gz ./pxc/sources/pxc/results/pxb80/pxb80.tar.gz; do
                                        sleep 5
                                    done

                                    until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG}/pxc80.tar.gz ./pxc/sources/pxc/results/pxc80.tar.gz; do
                                        sleep 5
                                    done

                                    export MTR_SUITES=${WORKER_6_MTR_SUITES}
                                    aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                    sg docker -c "
                                        if [ \$(docker ps -q | wc -l) -ne 0 ]; then
                                            docker ps -q | xargs docker stop --time 1 || :
                                        fi
                                        ./pxc/docker/run-test ${DOCKER_OS} 6
                                    "
                                '''
                            }
                            step([$class: 'JUnitResultArchiver', testResults: 'pxc/sources/pxc/results/*.xml', healthScaleFactor: 1.0])
                            archiveArtifacts 'pxc/sources/pxc/results/*.xml,pxc/sources/pxc/results/pxc80-test-mtr_logs.tar.gz'
                        }
                }
                stage('Test PXC80 - 7') {
                        when { 
                            beforeAgent true
                            expression { (env.WORKER_7_MTR_SUITES?.trim()) }
                        }
                        agent { label 'docker-32gb' }
                        steps {
                            git branch: 'parallel-mtr', url: 'https://github.com/kamil-holubicki/jenkins-pipelines'
                            echo 'Test PXC80'
                            withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c42456e5-c28d-4962-b32c-b75d161bff27', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                                sh '''
                                    echo "WORKER_7_MTR_SUITES: ${WORKER_7_MTR_SUITES}"
                                    
                                    sudo git reset --hard
                                    sudo git clean -xdf
                                    rm -rf pxc/sources/* || :
                                    sudo git -C sources reset --hard || :
                                    sudo git -C sources clean -xdf   || :

                                    until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG}/pxb24.tar.gz ./pxc/sources/pxc/results/pxb24/pxb24.tar.gz; do
                                        sleep 5
                                    done

                                    until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG}/pxb80.tar.gz ./pxc/sources/pxc/results/pxb80/pxb80.tar.gz; do
                                        sleep 5
                                    done

                                    until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG}/pxc80.tar.gz ./pxc/sources/pxc/results/pxc80.tar.gz; do
                                        sleep 5
                                    done

                                    export MTR_SUITES=${WORKER_7_MTR_SUITES}
                                    aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                    sg docker -c "
                                        if [ \$(docker ps -q | wc -l) -ne 0 ]; then
                                            docker ps -q | xargs docker stop --time 1 || :
                                        fi
                                        ./pxc/docker/run-test ${DOCKER_OS} 7
                                    "
                                '''
                            }
                            step([$class: 'JUnitResultArchiver', testResults: 'pxc/sources/pxc/results/*.xml', healthScaleFactor: 1.0])
                            archiveArtifacts 'pxc/sources/pxc/results/*.xml,pxc/sources/pxc/results/pxc80-test-mtr_logs.tar.gz'
                        }
                }
                stage('Test PXC80 - 8') {
                        when { 
                            beforeAgent true
                            expression { (env.WORKER_8_MTR_SUITES?.trim()) }
                        }
                        agent { label 'docker-32gb' }
                        steps {
                            git branch: 'parallel-mtr', url: 'https://github.com/kamil-holubicki/jenkins-pipelines'
                            echo 'Test PXC80'
                            withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c42456e5-c28d-4962-b32c-b75d161bff27', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                                sh '''
                                    echo "WORKER_8_MTR_SUITES: ${WORKER_8_MTR_SUITES}"
                                    
                                    sudo git reset --hard
                                    sudo git clean -xdf
                                    rm -rf pxc/sources/* || :
                                    sudo git -C sources reset --hard || :
                                    sudo git -C sources clean -xdf   || :

                                    until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG}/pxb24.tar.gz ./pxc/sources/pxc/results/pxb24/pxb24.tar.gz; do
                                        sleep 5
                                    done

                                    until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG}/pxb80.tar.gz ./pxc/sources/pxc/results/pxb80/pxb80.tar.gz; do
                                        sleep 5
                                    done

                                    until aws s3 cp --no-progress s3://pxc-build-cache/${BUILD_TAG}/pxc80.tar.gz ./pxc/sources/pxc/results/pxc80.tar.gz; do
                                        sleep 5
                                    done

                                    export MTR_SUITES=${WORKER_8_MTR_SUITES}
                                    aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                    sg docker -c "
                                        if [ \$(docker ps -q | wc -l) -ne 0 ]; then
                                            docker ps -q | xargs docker stop --time 1 || :
                                        fi
                                        ./pxc/docker/run-test ${DOCKER_OS} 8
                                    "
                                '''
                            }
                            step([$class: 'JUnitResultArchiver', testResults: 'pxc/sources/pxc/results/*.xml', healthScaleFactor: 1.0])
                            archiveArtifacts 'pxc/sources/pxc/results/*.xml,pxc/sources/pxc/results/pxc80-test-mtr_logs.tar.gz'
                        }
                }                
            }
        }
    }
    post {
        always {
            sh '''
                echo Finish: \$(date -u "+%s")
            '''
        }
    }
}
