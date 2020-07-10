# Property-Key-Checker

scans property keys from two URL based files and compares a local property file with them for changes.
 Normally the local file is an overwrite of zero or more keys in the first url based file, and should be migrated to instead overwrite based on the second url based file. 

## Example Usage

`./build/install/property-key-checker/bin/property-key-checker -i ./Language_de.properties -p https://raw.githubusercontent.com/liferay/liferay-portal/6.2/portal-impl/src/content/Language.properties -n https://raw.githubusercontent.com/liferay/liferay-portal/7.2.x/portal-impl/src/content/Language_de.properties`


## How to build

Built your own zip:
`./gradlew distZip`

Or just prepare an unzipped installation:
`./gradlew installDist`

To run the previously unzipped installation, call it like this:
`./build/install/property-key-checker/bin/property-key-checker
`

There is a help function: `./build/install/property-key-checker/bin/property-key-checker --help
`
