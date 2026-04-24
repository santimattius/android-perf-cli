class AndroidPerfCli < Formula
  desc "Android performance monitoring CLI (leaks, StrictMode, frames, ANR hints, Perfetto)"
  homepage "https://github.com/OWNER/android-perf-cli"
  url "https://github.com/OWNER/android-perf-cli/releases/download/v0.1.0/perf-cli-0.1.0.jar"
  version "0.1.0"
  sha256 "REPLACE_WITH_SHA256_FROM_RELEASE"
  license "Apache-2.0"

  depends_on "openjdk@17"

  def install
    libexec.install "perf-cli-0.1.0.jar" => "perf-cli.jar"
    bin.write_jar_script libexec/"perf-cli.jar", "perf"
  end

  test do
    assert_match "perf", shell_output("#{bin}/perf --help")
  end
end
