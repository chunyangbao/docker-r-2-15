#!/bin/sh

TEST_ROOT=$PWD
TASKLIB=$TEST_ROOT/src
INPUT_FILE_DIRECTORIES=$TEST_ROOT/data
S3_ROOT=s3://moduleiotest
WORKING_DIR=$TEST_ROOT/job_1111
RLIB=$TEST_ROOT/rlib

JOB_DEFINITION_NAME="R215_Generic"
JOB_ID=gp_job_RankNormalize_R215_$1
JOB_QUEUE=TedTest

COMMAND_LINE="Rscript $TASKLIB/run_rank_normalize.R $TASKLIB --input.file=$INPUT_FILE_DIRECTORIES/all_aml_train.gct --output.file.name=all_aml_train.NORM.gct"

DOCKER_CONTAINER=genepattern/docker-r-2-15
