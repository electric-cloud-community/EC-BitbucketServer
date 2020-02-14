// This procedure.dsl was generated automatically
// DO NOT EDIT THIS BLOCK === procedure_autogen starts ===
procedure 'Merge Pull Request', description: '''Test whether a pull request can be merged.''', {

    // Handling binary dependencies
    step 'flowpdk-setup', {
        description = "This step handles binary dependencies delivery"
        subprocedure = 'flowpdk-setup'
        actualParameter = [
            generateClasspathFromFolders: 'deps/libs'
        ]
    }

    step 'Merge Pull Request', {
        description = ''
        command = new File(pluginDir, "dsl/procedures/MergePullRequest/steps/MergePullRequest.groovy").text
        shell = 'ec-groovy'
        shell = 'ec-groovy -cp $[/myJob/flowpdk_classpath]'

        resourceName = '$[flowpdkResource]'

        postProcessor = '''$[/myProject/perl/postpLoader]'''
    }

    formalOutputParameter 'BitBucketMergeResponse',
        description: 'the response of the merge.'
// DO NOT EDIT THIS BLOCK === procedure_autogen ends, checksum: 57d7573edf34903b49c05f2f4bf7cf70 ===
// Do not update the code above the line
// procedure properties declaration can be placed in here, like
// property 'property name', value: "value"
}