import os
import re
import sys

def inject_hook(work_dir):
    # Locate SplashActivity.smali or LandscapeLauncherMainActivity.smali (avoiding deleted MainActivity.smali)
    target_file = None
    possible_names = ["SplashActivity.smali", "LandscapeLauncherMainActivity.smali", "MainActivity.smali"]
    
    for name in possible_names:
        for root, dirs, files in os.walk(work_dir):
            if name in files:
                # Skip the com/xj/psplay/main/MainActivity.smali which is deleted later anyway
                if "psplay" in root and name == "MainActivity.smali":
                    continue
                target_file = os.path.join(root, name)
                break
        if target_file:
            break
            
    if not target_file:
        print("ERROR: Entry activity smali file not found in decompiled directory.")
        sys.exit(1)
        
    print(f"Found target file at: {target_file}")
    
    with open(target_file, "r", encoding="utf-8") as f:
        content = f.read()
        
    # Check if already injected
    hook_call = "invoke-static {p0}, Lcom/gamehub/lite/extension/HybridInitializer;->init(Landroid/app/Activity;)V"
    if hook_call in content:
        print(f"Hook call already present in {os.path.basename(target_file)}.")
        return
        
    # Look for onCreate method signature (protected or public) and super.onCreate call
    on_create_pattern = r"(\.method\s+(?:protected|public)\s+onCreate\(Landroid/os/Bundle;\)V.*?invoke-super\s+\{[^}]+\},\s+L[^;]+;->onCreate\(Landroid/os/Bundle;\)V)"
    
    # We want to insert the hook immediately after the parent super.onCreate call
    match = re.search(on_create_pattern, content, re.DOTALL)
    if not match:
        print(f"ERROR: Could not locate onCreate method with super call in {target_file}")
        sys.exit(1)
        
    replacement = match.group(1) + f"\n\n    {hook_call}"
    new_content = content.replace(match.group(1), replacement)
    
    with open(target_file, "w", encoding="utf-8") as f:
        f.write(new_content)
        
    print(f"Successfully injected HybridInitializer hook into {os.path.basename(target_file)} onCreate!")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python inject-hook.py <decompiled_dir>")
        sys.exit(1)
    inject_hook(sys.argv[1])
