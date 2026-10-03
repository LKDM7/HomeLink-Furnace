param([switch]$IncludeGameplayResources)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$asset = Join-Path $project 'src/main/resources/assets/homelink_furnace'
$data = Join-Path $project 'src/main/resources/data'
@('textures/block','models/block','models/item','blockstates') | ForEach-Object { New-Item -ItemType Directory -Force -Path (Join-Path $asset $_) | Out-Null }
Add-Type -AssemblyName System.Drawing
# Native pixel textures with exact UVs and deterministic regeneration.
function Rect($x,$y,$w,$h,$hex) {
    $brush = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml($hex))
    $script:graphics.FillRectangle($brush,[single]$x,[single]$y,[single]$w,[single]$h); $brush.Dispose()
}
function Texture($name,$color,$kind) {
    $script:bitmap = [Drawing.Bitmap]::new(64,64)
    $script:graphics = [Drawing.Graphics]::FromImage($script:bitmap)
    $script:graphics.Clear([Drawing.Color]::Transparent)
    if ($kind -ne 'glass') {
        $base=[Drawing.ColorTranslator]::FromHtml($color)
        for($y=0;$y -lt 64;$y++){for($x=0;$x -lt 64;$x++){
            $grain=(($x*13+$y*29)%7)-3
            $script:bitmap.SetPixel($x,$y,[Drawing.Color]::FromArgb(255,[Math]::Max(0,[Math]::Min(255,$base.R+$grain)),[Math]::Max(0,[Math]::Min(255,$base.G+$grain)),[Math]::Max(0,[Math]::Min(255,$base.B+$grain))))
        }}
    }
    switch($kind) {
        'metal' { for($y=3;$y -lt 64;$y+=11){Rect 4 $y 55 1 '#78848a'};Rect 0 0 64 2 '#b2bdc1';Rect 0 62 64 2 '#475158' }
        'panel' { Rect 2 2 60 1 '#505a60';Rect 2 61 60 1 '#20272b';Rect 2 2 1 60 '#454f55';Rect 61 2 1 60 '#20272b';Rect 8 47 26 1 '#444e54';Rect 9 49 10 1 '#39434a' }
        'copper' { for($y=2;$y -lt 64;$y+=7){Rect 0 $y 64 1 '#ae7957'};Rect 1 0 2 64 '#d6a16e';Rect 61 0 2 64 '#6b4433' }
        'screen' {
            Rect 2 2 60 60 '#0c181d';Rect 4 4 56 2 '#527879';Rect 5 7 2 45 '#244448'
            Rect 12 12 4 19 '#a1c9b8';Rect 25 12 4 19 '#a1c9b8';Rect 16 20 9 4 '#a1c9b8'
            Rect 35 12 4 19 '#a1c9b8';Rect 39 12 13 4 '#a1c9b8';Rect 39 20 10 3 '#a1c9b8';Rect 39 27 13 4 '#a1c9b8'
            Rect 12 40 40 2 '#456e70';Rect 12 46 25 2 '#739d95';Rect 43 46 8 2 '#bb8c57';Rect 12 53 16 1 '#37575b'
        }
        'chamber' { Rect 1 1 62 2 '#272c30';for($y=9;$y -lt 62;$y+=10){Rect 0 $y 64 1 '#282e31';Rect 0 ($y+1) 64 1 '#101619'} }
        'coil' { for($x=4;$x -lt 64;$x+=8){Rect $x 0 2 64 '#645f59';Rect ($x+2) 0 1 64 '#292c2d'} }
        'heat' { for($x=0;$x -lt 64;$x+=8){Rect $x 0 2 64 '#e09d51';Rect ($x+3) 0 3 64 '#ffcf83'};Rect 0 29 64 6 '#ffe6ae' }
        'grille' { for($y=5;$y -lt 61;$y+=7){Rect 3 $y 58 3 '#10171b';Rect 3 ($y+3) 58 1 '#57636a'} }
        'roof' { Rect 2 2 60 1 '#626e74';Rect 2 61 60 1 '#1a2328';for($x=10;$x -lt 61;$x+=12){Rect $x 9 2 46 '#39444b'} }
        'glass' {
            # Sparse opaque reflections preserve chamber depth with cutout rendering.
            for($y=3;$y -lt 60;$y++){ $x=60-$y;Rect $x $y 2 1 '#415057';if($y -lt 31){Rect ($x-4) $y 1 1 '#2c3940'} }
            Rect 3 3 22 1 '#64747b';Rect 3 3 1 19 '#485960';Rect 53 60 8 1 '#233138'
        }
        {$_ -in 'input','output','energy'} {
            $accent=switch($kind){'input'{'#79a4b1'};'output'{'#bea477'};'energy'{'#bb8058'}}
            Rect 1 1 62 62 '#172025';Rect 3 3 58 58 $accent;Rect 6 6 52 52 '#253139'
            foreach($x in @(9,51)){foreach($y in @(9,51)){Rect $x $y 4 4 '#a4afb2';Rect ($x+1) ($y+1) 2 2 '#28343a'}}
            if($kind -eq 'energy') {
                Rect 33 15 8 11 '#ddb576';Rect 26 25 13 6 '#ddb576';Rect 24 31 9 7 '#ddb576';Rect 23 38 5 11 '#ddb576'
            } else {
                $arrow=if($kind -eq 'input'){'#aad0d3'}else{'#e0c599'}
                if($kind -eq 'input'){Rect 28 15 8 22 $arrow;Rect 20 32 24 5 $arrow;Rect 24 37 16 5 $arrow;Rect 28 42 8 5 $arrow}
                else{Rect 28 27 8 22 $arrow;Rect 20 27 24 5 $arrow;Rect 24 22 16 5 $arrow;Rect 28 17 8 5 $arrow}
            }
            Rect 15 54 34 2 '#56636a'
        }
    }
    $script:graphics.Dispose()
    $script:bitmap.Save((Join-Path $asset "textures/block/$name.png"),[Drawing.Imaging.ImageFormat]::Png)
    $script:bitmap.Dispose()
}
Texture steel '#2e353b' casing;Texture trim '#808b92' metal;Texture panel '#343e45' panel
Texture seal '#131b20' seal;Texture ceramic '#24292c' ceramic;Texture copper '#976648' copper
Texture chamber '#1a2227' chamber;Texture coil '#494944' coil;Texture heat '#e07b32' heat
Texture grille '#333e45' grille;Texture roof '#414c54' roof;Texture glass '#000000' glass
Texture screen '#173038' screen;Texture input '#253139' input;Texture output '#253139' output;Texture energy '#253139' energy
function WriteJson($path,$value) {
    New-Item -ItemType Directory -Force -Path (Split-Path $path -Parent) | Out-Null
    [IO.File]::WriteAllText($path,($value|ConvertTo-Json -Depth 40),[Text.UTF8Encoding]::new($false))
}
$textures=[ordered]@{particle='homelink_furnace:block/steel'}
foreach($key in @('steel','trim','panel','seal','ceramic','copper','chamber','coil','heat','grille','roof','glass','screen','input','output','energy')){$textures[$key]="homelink_furnace:block/$key"}
function Box([double[]]$from,[double[]]$to,[string]$texture,[hashtable]$overrides=@{}) {
    $faces=[ordered]@{}
    foreach($face in @('north','south','east','west','up','down')){
        $ref=if($overrides.ContainsKey($face)){$overrides[$face]}else{$texture}
        $faces[$face]=@{texture=$ref;uv=@(0,0,16,16)}
    }
    return @{from=$from;to=$to;faces=$faces}
}
function AddBox([double[]]$from,[double[]]$to,[string]$texture,[hashtable]$overrides=@{}) { $script:parts.Add((Box $from $to $texture $overrides)) }
function FrontPlate($x,$y,$w,$h,$texture) {
    AddBox @($x,$y,.40) @(($x+$w),($y+$h),.92) '#trim'
    AddBox @(($x+.2),($y+.2),.37) @(($x+$w-.2),($y+$h-.2),.40) '#seal' @{north=$texture}
}
function FanSeat($x,$y,$radius) {
    AddBox @(($x-$radius-.35),($y-$radius-.35),.48) @(($x+$radius+.35),($y+$radius+.35),1.2) '#trim'
    AddBox @(($x-$radius-.15),($y-$radius-.15),.46) @(($x+$radius+.15),($y+$radius+.15),.48) '#seal'
    AddBox @(($x-.15),($y-$radius-.3),.28) @(($x+.15),($y-$radius+.12),.36) '#trim'
    AddBox @(($x-.15),($y+$radius-.12),.28) @(($x+.15),($y+$radius+.3),.36) '#trim'
}
function BuildCabinet($tier) {
    $script:parts=[Collections.Generic.List[object]]::new()
    $W=if($tier -eq 1){16.0}else{32.0};$H=if($tier -eq 3){32.0}else{16.0};$D=$H
    $B=if($tier -eq 3){14.0}else{6.0};$T=if($tier -eq 3){27.0}else{12.8}
    AddBox @(.7,1.2,1.2) @(1.9,($H-.7),($D-.7)) '#steel'
    AddBox @(($W-1.9),1.2,1.2) @(($W-.7),($H-.7),($D-.7)) '#steel'
    AddBox @(1.9,1.2,($D-1.8)) @(($W-1.9),($H-.7),($D-.7)) '#panel'
    AddBox @(1.9,1.2,1.2) @(($W-1.9),2.5,($D-1.8)) '#steel'
    AddBox @(1.9,($H-1.8),1.2) @(($W-1.9),($H-.7),($D-1.8)) '#roof'
    foreach($x in @(.35,($W-1.15))){foreach($z in @(.65,($D-1.45))){
        AddBox @($x,1.2,$z) @(($x+.8),($H-.8),($z+.8)) '#trim'
    }}
    foreach($x in @(.6,($W-2.9))){foreach($z in @(.6,($D-2.9))){
        AddBox @($x,0,$z) @(($x+2.3),1.2,($z+2.3)) '#seal'
        AddBox @(($x+.15),.8,($z+.15)) @(($x+2.15),1.5,($z+2.15)) '#trim'
    }}
    AddBox @(1.1,($H-1.5),.85) @(($W-1.1),($H-.55),1.8) '#trim'
    AddBox @(1.1,1.15,.85) @(($W-1.1),2.0,1.8) '#trim'
    AddBox @(1.6,2.0,.85) @(($W-1.6),($B-.65),2.2) '#panel'
    AddBox @(1.6,$T,.85) @(($W-1.6),($H-1.5),2.2) '#panel'
    AddBox @(1.65,($B-.7),.60) @(($W-1.65),($B+.1),2.1) '#trim'
    AddBox @(1.65,($T-.1),.60) @(($W-1.65),($T+.7),2.1) '#trim'
    AddBox @(1.65,($B+.1),.60) @(2.8,($T-.1),2.1) '#trim'
    AddBox @(($W-2.8),($B+.1),.60) @(($W-1.65),($T-.1),2.1) '#trim'
    AddBox @(2.8,$B,.80) @(3.1,$T,1.8) '#seal'
    AddBox @(($W-3.1),$B,.80) @(($W-2.8),$T,1.8) '#seal'
    AddBox @(3.1,$B,.80) @(($W-3.1),($B+.35),1.8) '#seal'
    AddBox @(3.1,($T-.35),.80) @(($W-3.1),$T,1.8) '#seal'
    AddBox @(2.9,($B+.35),3.15) @(($W-2.9),($T-.35),4.1) '#chamber'
    AddBox @(2.7,($B+.35),1.8) @(3.4,($T-.35),3.3) '#ceramic'
    AddBox @(($W-3.4),($B+.35),1.8) @(($W-2.7),($T-.35),3.3) '#ceramic'
    AddBox @(3.2,($B+.1),1.05) @(($W-3.2),($B+.6),3.35) '#ceramic'
    $rows=if($tier -eq 3){@(16.0,20.0,24.0)}else{@(7.2,9.2,11.2)}
    $rodHeight=if($tier -eq 3){.7}else{.45}
    foreach($y in $rows){
        AddBox @(4,$y,1.90) @(($W-4),($y+$rodHeight),2.15) '#coil'
        AddBox @(3.3,($y-.15),1.70) @(4,($y+$rodHeight+.15),2.3) '#copper'
        AddBox @(($W-4),($y-.15),1.70) @(($W-3.3),($y+$rodHeight+.15),2.3) '#copper'
    }
    for($x=4;$x -lt $W-4;$x+=4){AddBox @($x,($B+.63),2.3) @(($x+1),($B+.83),3.05) '#trim'}
    $glass=Box @(3.15,($B+.38),.92) @(($W-3.15),($T-.38),.95) '#glass'
    $glass.faces=@{north=@{texture='#glass';uv=@(0,0,16,16)};south=@{texture='#glass';uv=@(0,0,16,16)}}
    $script:parts.Add($glass)
    AddBox @(($W-2.2),($B+1.2),.18) @(($W-1.85),($T-1.2),.58) '#copper'
    if($tier -eq 3){
        FrontPlate 2.5 7.1 10.0 4.0 '#screen';FrontPlate 2.5 3.3 10.0 2.4 '#panel'
        FanSeat 19.5 6.5 2.2;FanSeat 26.0 6.5 2.2
        AddBox @(13.7,3,.75) @(14.1,11.7,1.45) '#trim'
    }else{
        FrontPlate 2.4 2.3 6.0 2.6 '#screen';FanSeat ($W-4.5) 3.1 1.25
        if($tier -eq 2){FrontPlate 10.5 2.4 8.0 2.3 '#panel'}
    }
    for($i=0;$i -lt $tier;$i++){AddBox @((3.5+$i*1.0),($H-2.7),.75) @((3.9+$i*1.0),($H-1.85),.84) '#copper'}
    foreach($side in @('left','right')){
        $a=if($side -eq 'left'){.45}else{$W-.75};$b=$a+.3
        AddBox @($a,3.2,3) @($b,($H-3),($D-3)) '#panel'
        for($z=4;$z -lt $D-3;$z+=4){AddBox @(($a-.10),4,$z) @(($b+.10),($H-4),($z+.45)) '#steel'}
    }
    AddBox @(2.4,3.0,($D-.69)) @(($W-2.4),($H-3),($D-.30)) '#grille'
    for($y=4;$y -lt $H-3;$y+=2.8){AddBox @(3,$y,($D-.27)) @(($W-3),($y+.45),($D-.13)) '#trim'}
    $inZ=if($tier -eq 3){24.0}else{8.0}
    AddBox @(4,($H-.55),($inZ-4)) @(12,($H-.22),($inZ+4)) '#trim'
    AddBox @(4.6,($H-.21),($inZ-3.4)) @(11.4,($H-.10),($inZ+3.4)) '#seal' @{up='#input'}
    for($col=0;$col -lt $W/16;$col++){for($row=0;$row -lt $D/16;$row++){
        if($col -eq 0 -and $row -eq $D/16-1){continue}
        $x=$col*16+4;$z=$row*16+4
        AddBox @($x,($H-.55),$z) @(($x+8),($H-.25),($z+8)) '#seal'
        for($i=0;$i -lt 5;$i++){AddBox @(($x+.5+$i*1.5),($H-.24),($z+.6)) @(($x+1.1+$i*1.5),($H-.10),($z+7.4)) '#trim'}
    }}
    $outX=if($tier -eq 1){8.0}else{24.0}
    AddBox @(($outX-3.8),3.2,($D-.35)) @(($outX+3.8),10.8,($D-.07)) '#trim'
    AddBox @(($outX-3.4),3.6,($D-.06)) @(($outX+3.4),10.4,($D-.02)) '#seal' @{south='#output'}
    AddBox @(($W-.55),3.2,4.2) @(($W-.12),10.8,11.8) '#copper'
    AddBox @(($W-.11),3.6,4.6) @(($W-.02),10.4,11.4) '#seal' @{east='#energy'}
    AddBox @(2.0,2.1,($D-.22)) @(($W-2.0),2.55,($D-.07)) '#copper'
    return $script:parts.ToArray()
}
# Partition one cabinet at cell boundaries, preserving face UVs and omitting internal cuts.
function ClipBox($element,[double[]]$cellFrom,[double[]]$cellTo) {
    $from=@();$to=@()
    for($i=0;$i -lt 3;$i++){
        $from+=[Math]::Max($element.from[$i],$cellFrom[$i]);$to+=[Math]::Min($element.to[$i],$cellTo[$i])
        if($to[$i]-$from[$i] -lt .00001){return $null}
    }
    $faces=[ordered]@{}
    $faceAxes=@{north=@(2,0,0,1,$true,$true);south=@(2,1,0,1,$false,$true);east=@(0,1,2,1,$true,$true);west=@(0,0,2,1,$false,$true);up=@(1,1,0,2,$false,$false);down=@(1,0,0,2,$false,$true)}
    foreach($face in $element.faces.Keys){
        $spec=$faceAxes[$face];$normal=[int]$spec[0];$positive=[int]$spec[1]
        if($positive -eq 1){if([Math]::Abs($to[$normal]-$element.to[$normal]) -gt .00001){continue}}
        elseif([Math]::Abs($from[$normal]-$element.from[$normal]) -gt .00001){continue}
        $u=[int]$spec[2];$v=[int]$spec[3];$du=$element.to[$u]-$element.from[$u];$dv=$element.to[$v]-$element.from[$v]
        $u0=($from[$u]-$element.from[$u])/$du;$u1=($to[$u]-$element.from[$u])/$du
        $v0=($from[$v]-$element.from[$v])/$dv;$v1=($to[$v]-$element.from[$v])/$dv
        if($spec[4]){$temp=$u0;$u0=1-$u1;$u1=1-$temp};if($spec[5]){$temp=$v0;$v0=1-$v1;$v1=1-$temp}
        $original=$element.faces[$face];$uv=$original.uv
        $faces[$face]=@{texture=$original.texture;uv=@(($uv[0]+($uv[2]-$uv[0])*$u0),($uv[1]+($uv[3]-$uv[1])*$v0),($uv[0]+($uv[2]-$uv[0])*$u1),($uv[1]+($uv[3]-$uv[1])*$v1))}
    }
    if($faces.Count -eq 0){return $null}
    $localFrom=@();$localTo=@();for($i=0;$i -lt 3;$i++){$localFrom+=($from[$i]-$cellFrom[$i]);$localTo+=($to[$i]-$cellFrom[$i])}
    return @{from=$localFrom;to=$localTo;faces=$faces}
}
for($tier=1;$tier -le 3;$tier++){
    $w=if($tier -eq 1){1}else{2};$h=if($tier -eq 3){2}else{1};$d=$h
    $parts=@(BuildCabinet $tier);$variants=[ordered]@{}
    for($col=0;$col -lt $w;$col++){for($layer=0;$layer -lt $h;$layer++){for($row=0;$row -lt $d;$row++){
        $name="industrial_furnace_$($tier)_$($col)_$($layer)_$($row)";$elements=@()
        foreach($part in $parts){$clipped=ClipBox $part @(($col*16),($layer*16),($row*16)) @((($col+1)*16),(($layer+1)*16),(($row+1)*16));if($null -ne $clipped){$elements+=$clipped}}
        WriteJson (Join-Path $asset "models/block/$name.json") @{parent='minecraft:block/block';render_type='minecraft:cutout';ambientocclusion=$true;textures=$textures;elements=$elements}
        foreach($facing in @('north','east','south','west')){$variants["facing=$facing,column=$col,layer=$layer,row=$row"]=@{model="homelink_furnace:block/$name";y=@{north=0;east=90;south=180;west=270}[$facing];uvlock=$true}}
    }}}
    foreach($col in @(0,1)){foreach($layer in @(0,1)){foreach($row in @(0,1)){foreach($facing in @('north','east','south','west')){
        $key="facing=$facing,column=$col,layer=$layer,row=$row"
        if(!$variants.Contains($key)){$variants[$key]=@{model="homelink_furnace:block/industrial_furnace_$($tier)_0_0_0";y=@{north=0;east=90;south=180;west=270}[$facing];uvlock=$true}}
    }}}}
    WriteJson (Join-Path $asset "blockstates/industrial_furnace_$tier.json") @{variants=$variants}
    $itemElements=@();$offset=@((($w-1)*8),(($h-1)*8),(($d-1)*8))
    foreach($part in $parts){$from=@();$to=@();for($i=0;$i -lt 3;$i++){$from+=($part.from[$i]-$offset[$i]);$to+=($part.to[$i]-$offset[$i])};$itemElements+=@{from=$from;to=$to;faces=$part.faces}}
    $scale=if($tier -eq 1){.62}elseif($tier -eq 2){.43}else{.36}
    WriteJson (Join-Path $asset "models/item/industrial_furnace_$tier.json") @{parent='minecraft:block/block';render_type='minecraft:cutout';textures=$textures;elements=$itemElements;display=@{gui=@{rotation=@(25,225,0);translation=@(0,0,0);scale=@($scale,$scale,$scale)};fixed=@{rotation=@(0,0,0);translation=@(0,0,0);scale=@(.4,.4,.4)};ground=@{rotation=@(0,0,0);translation=@(0,2,0);scale=@(.25,.25,.25)};thirdperson_righthand=@{rotation=@(75,45,0);translation=@(0,2.5,0);scale=@(.3,.3,.3)}}}
    $machineWidth=$w*16;$rows=if($tier -eq 3){@(16.0,20.0,24.0)}else{@(7.2,9.2,11.2)};$rodHeight=if($tier -eq 3){.7}else{.45}
    $heatElements=@();foreach($y in $rows){$heatElements+=(Box @(3.99,($y-.01),1.89) @(($machineWidth-3.99),($y+$rodHeight+.01),2.16) '#heat')}
    WriteJson (Join-Path $asset "models/block/heat_$tier.json") @{render_type='minecraft:cutout';ambientocclusion=$false;textures=@{particle='homelink_furnace:block/heat';heat='homelink_furnace:block/heat'};elements=$heatElements}
    Write-Output "Tier $tier : $($parts.Count) cabinet pieces, $($w*$h*$d) world cells."
}
$fanElements=@((Box @(-.42,-.42,-.03) @(.42,.42,.32) '#copper'))
foreach($r in @(0,90,180,270)){
    $angle=$r*[Math]::PI/180;$points=@()
    foreach($x in @(.24,1.9)){foreach($y in @(-.25,.45)){$points+=,@(($x*[Math]::Cos($angle)-$y*[Math]::Sin($angle)),($x*[Math]::Sin($angle)+$y*[Math]::Cos($angle)))}}
    $from=@((($points|ForEach-Object {$_[0]}|Measure-Object -Minimum).Minimum),(($points|ForEach-Object {$_[1]}|Measure-Object -Minimum).Minimum),.02)
    $to=@((($points|ForEach-Object {$_[0]}|Measure-Object -Maximum).Maximum),(($points|ForEach-Object {$_[1]}|Measure-Object -Maximum).Maximum),.17)
    $fanElements+=(Box $from $to '#trim')
}
WriteJson (Join-Path $asset 'models/block/fan.json') @{textures=@{particle='homelink_furnace:block/trim';trim='homelink_furnace:block/trim';copper='homelink_furnace:block/copper'};elements=$fanElements}
WriteJson (Join-Path $asset 'models/block/heat.json') @{parent='homelink_furnace:block/heat_1'}
if($IncludeGameplayResources){
    $blocks=@('homelink_furnace:industrial_furnace_1','homelink_furnace:industrial_furnace_2','homelink_furnace:industrial_furnace_3')
    WriteJson (Join-Path $data 'minecraft/tags/block/mineable/pickaxe.json') @{replace=$false;values=$blocks}
    WriteJson (Join-Path $data 'minecraft/tags/block/needs_iron_tool.json') @{replace=$false;values=$blocks}
    $recipes=@(
        @{pattern=@('ICI','MFB','IRI');key=@{I=@{item='minecraft:iron_ingot'};C=@{item='minecraft:copper_ingot'};M=@{item='homecore:homelink_control_module'};F=@{item='minecraft:furnace'};R=@{item='minecraft:redstone'};B=@{item='homecore:homelink_circuit_board'}}},
        @{pattern=@('GPG','MFC','IBI');key=@{G=@{item='minecraft:gold_ingot'};P=@{item='homecore:homelink_microprocessor'};M=@{item='homecore:homelink_control_module'};F=@{item='homelink_furnace:industrial_furnace_1'};C=@{item='homecore:homelink_communication_module'};I=@{item='minecraft:iron_ingot'};B=@{item='minecraft:copper_ingot'}}},
        @{pattern=@('DMB','CFM','INI');key=@{D=@{item='minecraft:diamond'};M=@{item='homecore:homelink_control_module'};B=@{item='minecraft:copper_block'};C=@{item='homecore:homelink_communication_module'};F=@{item='homelink_furnace:industrial_furnace_2'};I=@{item='minecraft:iron_block'};N=@{item='minecraft:netherite_ingot'}}}
    )
    for($tier=1;$tier -le 3;$tier++){
        $recipe=$recipes[$tier-1];$recipe.type='minecraft:crafting_shaped';$recipe.category='misc';$recipe.result=@{id="homelink_furnace:industrial_furnace_$tier";count=1}
        WriteJson (Join-Path $data "homelink_furnace/recipe/industrial_furnace_$tier.json") $recipe
        WriteJson (Join-Path $data "homelink_furnace/loot_table/blocks/industrial_furnace_$tier.json") @{type='minecraft:block';pools=@()}
    }
}
Write-Output 'Furnace 64px textures, complete cabinet models, UV-clipped world cells and tier-specific heat meshes generated.'
