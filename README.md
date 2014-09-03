tail4j
====

Tail for Java

## Requirement

-  Java 1.7 (JDK 1.7) or higher
-  Maven 3.x

## Build

	$ mvn package

## Usage

	$ cd target/appassembler/bin
	$ tail4j [options...] filepath

	$ tail4j -h
	tail4j [options...] watch-file-path
	 -P (pos-file) FILE : persist last reading position to POS-FILE (default = /<java.io.tmpdir>/<TEMPORARY-FILE>)
	 -e (encode) VAL: source file encoding (default = Platform's default charset)
	 -h (help)  : show this message
	 -p (persistence)   : persist last reading position (default = false)
	 -r (reset) : reset previous reading position (default = false)
	 Example: tail4j -P (pos-file) FILE -e (encode) VAL -h (help) -p (persistence) -r (reset)

## Licence

Apache License Version 2.0 http://apache.org/licenses/LICENSE-2.0.txt
