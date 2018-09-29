#! /usr/bin/env sh

for src_file in $(find $1 -regex '.*\.java'); do
	echo "    FMT   $src_file"
	clang-format -i -style=file $src_file
done
