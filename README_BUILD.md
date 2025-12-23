## Build order
1) Build/install main plugin
- cd RoflRPG
- mvn -DskipTests package
- mvn -DskipTests install

2) Build the addon
- cd RoflRPGSkillsAddon
- mvn -DskipTests package

Result jars will be in each module's target/ folder.
