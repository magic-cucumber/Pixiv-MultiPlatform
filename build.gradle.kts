gradle.taskGraph.whenReady {
    if (allTasks.any { it.name.startsWith("compileKotlin") }) {
        val problemsReport = rootProject.layout.buildDirectory
            .file("reports/problems/problems-report.html")
            .get()
            .asFile

        if (problemsReport.exists()) {
            problemsReport.delete()
        }
    }
}
