
echo "R2.7 POST RUN CUSTOM: PERFORMING aws s3 sync $R_LIBS $S3_ROOT$R_LIBS_S3"
aws s3 sync $R_LIBS $S3_ROOT$R_LIBS_S3 --quiet

