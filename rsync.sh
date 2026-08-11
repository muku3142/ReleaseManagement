#!/bin/bash

DIR=/home/evision/release-management/ReleaseManagement

mkdir -p "$DIR"

rsync -a * "$DIR"