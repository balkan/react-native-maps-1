
install:
	cd /tmp/; rm -rf react-native-maps
	cd ~/workspace/CheckFeel/node_modules/; rm -rf react-native-maps

	cd ~/workspace/; rm maps.tgz; tar cfz maps.tgz react-native-maps-1; tar xfz maps.tgz -C /tmp/
	cd /tmp/; mv react-native-maps-1 react-native-maps
	cd /tmp/react-native-maps/; rm -rf .git* LICENCE Makefile README.md node_modules


	cd /tmp/; tar cfz maps.tgz react-native-maps
	cd /tmp/; tar xfz maps.tgz  -C ~/workspace/CheckFeel/node_modules/
