class AndroidPerfCli < Formula
  desc "Android performance monitoring CLI (leaks, StrictMode, frames, ANR hints, Perfetto)"
  homepage "https://github.com/santimattius/android-perf-cli"
  url "https://github.com/santimattius/android-perf-cli/releases/download/v0.2.0/perf-cli-0.2.0.jar"
  version "0.2.0"
  sha256 "643d26aa781978172de6241aade2400e10ffb46718e64c46ad470bcec09701eb"
  license "Apache-2.0"

  depends_on "openjdk@17"

  def install
    libexec.install "perf-cli-0.2.0.jar" => "perf-cli.jar"
    bin.write_jar_script libexec/"perf-cli.jar", "perf"
  end

  test do
    assert_match "perf", shell_output("#{bin}/perf --help")
  end
end
