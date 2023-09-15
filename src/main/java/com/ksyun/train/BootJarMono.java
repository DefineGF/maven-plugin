package com.ksyun.train;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.util.List;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Mojo(name="bootJar")
public class BootJarMono extends AbstractMojo {
    @Parameter(property = "main.class", required = true)
    private String mainClass;

    @Component
    protected MavenProject project; // 输出的是使用插件的 project 信息

    @Parameter(defaultValue = "${project.build.finalName}", readonly = true)
    private String finalName; // artifactId-version

    @Parameter(defaultValue = "${project.packaging}")
    private String packagingSuffix;  // 打包后缀名

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private String targetDirPath;  // jar 输出目录：target

    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    private String classesDirPath; // target/classes

    public final static String MANIFEST_NAME = "MANIFEST.MF";

    private List<File> dependencyFiles = null;


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("start package~~~");

        dependencyFiles = project.getDependencyArtifacts()
                .stream()
                .map(Artifact::getFile)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        File zipFile = new File(targetDirPath + "/" + finalName + ".zip");
        File jarTempFile = new File(targetDirPath + "/" + finalName + "." + packagingSuffix);

        writeToJar(jarTempFile);
        writeToZip(zipFile, jarTempFile);
        if (jarTempFile.exists()) {
            jarTempFile.delete();
        }

        getLog().info("package successful!!!");
    }

    private void writeToJar(File jarFile) {
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile))) {
            // 将 classes 中的文件写入 jar 文件
            File classesDir = new File(classesDirPath);
            if (!classesDir.exists()) {
                return;
            }
            for (File subFile : Objects.requireNonNull(classesDir.listFiles())) {
                writeClassToJar(jos, subFile, subFile.getName());
            }

            // 将 manifest 文件写入 jar 文件
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

            StringBuilder sb = new StringBuilder();
            for (File file : dependencyFiles) {
                sb.append("lib/").append(file.getName()).append(" ");
            }
            manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, sb.toString());

            manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass); // 添加Main-Class

            JarEntry entry = new JarEntry("META-INF/" + MANIFEST_NAME);
            jos.putNextEntry(entry);
            manifest.write(jos);
        } catch (IOException e) {
            System.out.println("写入目标 jar 文件失败，信息如下：" + e.getMessage());
            e.printStackTrace();
        }
    }

    private void writeToZip(File zipFile, File jarFile) {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zipFile))) {

            // 将依赖文件放入 lib 目录并写入 zip
            for (File file : dependencyFiles) {
                ZipEntry zipEntry = new ZipEntry("lib/" + file.getName());
                zipOutputStream.putNextEntry(zipEntry);
                writeFileToOutputStream(file.getPath(), zipOutputStream);
            }

            // 将目标 jar 文件写入 zip 文件
            zipOutputStream.putNextEntry(new ZipEntry(jarFile.getName()));
            writeFileToOutputStream(jarFile.getPath(), zipOutputStream);
        } catch (IOException e) {
            System.out.println("写入目标 zip 文件失败，信息如下：" + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * 递归写入 class 文件
     */
    private void writeClassToJar(JarOutputStream jarOut, File file, String path)
            throws IOException {
        if (file == null || !file.exists()) return;

        if (file.isDirectory()) {
            path = path + "/";
            for (File subFile : Objects.requireNonNull(file.listFiles())) {
                String subPath = path  + subFile.getName();
                writeClassToJar(jarOut, subFile, subPath);
            }
        } else {
            JarEntry jarEntry = new JarEntry(path);
            jarOut.putNextEntry(jarEntry);

            FileInputStream fis = new FileInputStream(file);
            byte[] bytes = new byte[1024];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
                jarOut.write(bytes, 0, length);
            }
            fis.close();
        }
    }



    /**
     * 将文件写入目标 OutputStream
     */
    private void writeFileToOutputStream(String sourceFile, OutputStream outputStream)
            throws IOException {
        BufferedInputStream fileInputStream = new BufferedInputStream(new FileInputStream(sourceFile));
        byte[] buffer = new byte[1024];
        int len;
        while ((len = fileInputStream.read(buffer)) > 0){
            outputStream.write(buffer, 0, len);
        }
        fileInputStream.close();
    }
}
