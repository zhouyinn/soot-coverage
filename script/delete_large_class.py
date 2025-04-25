import os
from pathlib import Path
from tqdm import tqdm

def del_large_file():
    # Customize this path to your test-classes directory
    base_path = Path("../checkstyle-8.35/target/test-classes")

    # Bytecode size limit that may cause ArrayIndexOutOfBoundsException
    MAX_CLASS_SIZE = 1000

    # Collect all .class files
    class_files = list(base_path.rglob("*.class"))
    print(f"🔍 Found {len(class_files)} .class files under {base_path}")

    deleted_files = []

    # Scan with progress bar
    for class_file in tqdm(class_files, desc="Scanning .class files"):
        if class_file.stat().st_size > MAX_CLASS_SIZE:
            deleted_files.append(str(class_file))
            class_file.unlink()  # Delete the file

    # Summary
    print(f"\n✅ Scan complete.")
    print(f"🗑️ Deleted {len(deleted_files)} files over {MAX_CLASS_SIZE} bytes.")
    if deleted_files:
        print("\nList of deleted files:")
        for path in deleted_files:
            print(f" - {path}")
    else:
        print("No oversized files found.")

if __name__ == "__main__":
    del_large_file()