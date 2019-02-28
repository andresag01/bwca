#! /usr/bin/env sh

CLANG_FORMAT="clang-format-6.0"

for src_file in $(find $1 -regex '.*\.java'); do
	echo "    FMT   $src_file"
	$CLANG_FORMAT -i -style=file $src_file
done
