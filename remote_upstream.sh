#!/bin/bash
git remote -v

#git remote add upstream git@github.com:google-ar/arcore-android-sdk.git 
git remote add upstream https://github.com/google-ar/arcore-android-sdk.git 
git fetch upstream
git merge upstream/master
git push
