import os
import re
import sys

def inject_hook(work_dir):
    # Locate MainActivity.smali inside the decompiled work directory
    target_file = None
    for root, dirs, files in os.walk(work_dir):
        if "MainActivity.smali" in files:
            target_file = os.path.join(root, "MainActivity.smali")
            break
            
    if not target_file:
        print("ERROR: MainActivity.smali not found in decompiled directory.")
        sys.exit(1)
        
    print(f"Found MainActivity.smali at: {target_file}")
    
    with open(target_file, "r", encoding="utf-8") as f:
        content = f.read()
        
    # Check if already injected
    hook_call = "invoke-static {p0}, Lcom/gamehub/lite/extension/HybridInitializer;->init(Landroid/app/Activity;)V"
    if hook_call in content:
        print("Hook call already present in MainActivity.smali.")
        return
        
    # Look for onCreate method signature
    on_create_pattern = r"(\.method protected onCreate\(Landroid/os/Bundle;\)V.*?invoke-super \{p0, p1\}, L.*?->onCreate\(Landroid/os/Bundle;\)V)"
    
    # We want to insert the hook immediately after the parent super.onCreate call
    match = re.search(on_create_pattern, content, re.DOTALL)
    if not match:
        print("ERROR: Could not locate onCreate method with super call in MainActivity.smali")
        sys.exit(1)
        
    replacement = match.group(1) + f"\n\n    {hook_call}"
    new_content = content.replace(match.group(1), replacement)
    
    with open(target_file, "w", encoding="utf-8") as f:
        f.write(new_content)
        
    print("Successfully injected HybridInitializer hook into MainActivity.onCreate!")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python inject-hook.py <decompiled_dir>")
        sys.exit(1)
    inject_hook(sys.argv[1])
